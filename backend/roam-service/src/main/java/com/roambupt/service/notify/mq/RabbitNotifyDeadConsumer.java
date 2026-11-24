package com.roambupt.service.notify.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 死信自动补偿：消费死信队列，重投回主队列。
 *
 * <p>靠 RabbitMQ 写入的 x-death 轮次判断重试次数，超过 3 次就丢弃并告警，
 * 避免坏消息在 DLQ 和主队列之间无限循环。</p>
 */
@Component
@ConditionalOnProperty(name = "roam.notify.mq.enabled", havingValue = "true")
public class RabbitNotifyDeadConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitNotifyDeadConsumer.class);
    private static final int MAX_DEATHS = 3;

    private final ObjectMapper objectMapper;
    private final NotifyMqSender sender;
    private final Counter replayed;
    private final Counter dropped;

    public RabbitNotifyDeadConsumer(ObjectMapper objectMapper,
                                    NotifyMqSender sender,
                                    MeterRegistry meterRegistry) {
        this.objectMapper = objectMapper;
        this.sender = sender;
        this.replayed = Counter.builder("notify.dlq.replayed").register(meterRegistry);
        this.dropped = Counter.builder("notify.dlq.dropped").register(meterRegistry);
    }

    @RabbitListener(queues = "${roam.notify.mq.dead-queue:roam.notify.dead.queue}")
    public void onDead(Message message, Channel channel,
                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws Exception {
        int deaths = countDeaths(message);
        try {
            NotifyMessage payload = objectMapper.readValue(message.getBody(), NotifyMessage.class);
            if (deaths < MAX_DEATHS) {
                sender.send(payload, null);
                replayed.increment();
                log.warn("notify.dlq.replayed type={} source={} deaths={}",
                        payload.type(), payload.sourceId(), deaths);
            } else {
                dropped.increment();
                log.error("notify.dlq.dropped type={} source={} deaths={} 已达上限",
                        payload.type(), payload.sourceId(), deaths);
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.warn("notify.dlq.handle.failed reason={}", e.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @SuppressWarnings("unchecked")
    private int countDeaths(Message message) {
        List<Map<String, ?>> xDeath = message.getMessageProperties().getXDeathHeader();
        if (xDeath == null || xDeath.isEmpty()) {
            return 0;
        }
        Object count = xDeath.get(0).get("count");
        return count instanceof Number number ? number.intValue() : 0;
    }
}
