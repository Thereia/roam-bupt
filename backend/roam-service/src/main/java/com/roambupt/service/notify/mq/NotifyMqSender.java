package com.roambupt.service.notify.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roambupt.service.notify.config.NotifyMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 可靠投递：统一负责“发出去 + 等确认 + 失败落库”。
 *
 * <p>业务发布和 outbox 重投都走这里：
 * confirm ack → outbox 标记 SENT；nack / return / 超时未确认 → 标记失败或落库，
 * 由 {@link NotifyOutboxRetryScheduler} 再投。</p>
 */
@Component
@ConditionalOnProperty(name = "roam.notify.mq.enabled", havingValue = "true")
public class NotifyMqSender {

    private static final Logger log = LoggerFactory.getLogger(NotifyMqSender.class);

    private record Pending(NotifyMessage message, Long outboxId, long createdAt) {
    }

    private final RabbitTemplate rabbitTemplate;
    private final NotifyOutboxService outboxService;
    private final ObjectMapper objectMapper;
    private final NotifyMqProperties properties;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public NotifyMqSender(RabbitTemplate rabbitTemplate,
                          NotifyOutboxService outboxService,
                          ObjectMapper objectMapper,
                          NotifyMqProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.outboxService = outboxService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @PostConstruct
    void initCallbacks() {
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback((correlation, ack, cause) -> {
            if (correlation == null) {
                return;
            }
            Pending item = pending.remove(correlation.getId());
            if (item == null) {
                return;
            }
            if (ack) {
                if (item.outboxId() != null) {
                    outboxService.markSentById(item.outboxId());
                }
            } else {
                log.warn("notify.mq.nack type={} source={} cause={}",
                        item.message().type(), item.message().sourceId(), cause);
                failed(item);
            }
        });
        rabbitTemplate.setReturnsCallback(returned -> {
            String correlationId = (String) returned.getMessage().getMessageProperties()
                    .getHeader("spring_returned_message_correlation");
            Pending item = correlationId == null ? null : pending.remove(correlationId);
            log.warn("notify.mq.return routingKey={} reply={}", returned.getRoutingKey(), returned.getReplyText());
            if (item != null) {
                failed(item);
                return;
            }
            try {
                NotifyMessage message = objectMapper.readValue(returned.getMessage().getBody(), NotifyMessage.class);
                outboxService.save(message);
            } catch (Exception e) {
                log.error("notify.mq.return.parse.failed reason={}", e.getMessage());
            }
        });
    }

    /** 业务侧发布：事务内会等提交后再发；失败自动落库。 */
    public void publish(NotifyMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(message, null);
                }
            });
            return;
        }
        send(message, null);
    }

    /** 发送并跟踪确认；outboxId 非空表示这是重投，确认后要把 outbox 标成 SENT。 */
    public void send(NotifyMessage message, Long outboxId) {
        String id = UUID.randomUUID().toString();
        pending.put(id, new Pending(message, outboxId, System.currentTimeMillis()));
        try {
            rabbitTemplate.convertAndSend(properties.getExchange(), properties.getRoutingKey(),
                    message, new CorrelationData(id));
            log.debug("notify.mq.sent type={} source={} outboxId={}",
                    message.type(), message.sourceId(), outboxId);
        } catch (Exception e) {
            pending.remove(id);
            log.warn("notify.mq.send.failed type={} reason={}", message.type(), e.getMessage());
            failed(new Pending(message, outboxId, System.currentTimeMillis()));
        }
    }

    /** 等不到确认的消息：重投失败标记，新消息落库。 */
    @Scheduled(fixedDelay = 60_000L)
    public void expireUnconfirmed() {
        long deadline = System.currentTimeMillis() - properties.getConfirmTimeoutMs();
        pending.forEach((id, item) -> {
            if (item.createdAt() < deadline && pending.remove(id, item)) {
                log.warn("notify.mq.confirm.timeout type={} source={}", item.message().type(), item.message().sourceId());
                failed(item);
            }
        });
    }

    private void failed(Pending item) {
        if (item.outboxId() != null) {
            outboxService.markFailedById(item.outboxId());
        } else {
            outboxService.save(item.message());
        }
    }
}
