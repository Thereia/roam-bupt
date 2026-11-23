package com.roambupt.service.notify.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** RabbitMQ 通知发布器：只在 roam.notify.mq.enabled=true 时注册。 */
@Component
@ConditionalOnProperty(name = "roam.notify.mq.enabled", havingValue = "true")
public class RabbitNotifyPublisher implements NotifyPublisher {

    private static final Logger log = LoggerFactory.getLogger(RabbitNotifyPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;

    public RabbitNotifyPublisher(RabbitTemplate rabbitTemplate,
                                 @Value("${roam.notify.mq.exchange:roam.notify.exchange}") String exchange,
                                 @Value("${roam.notify.mq.routing-key:roam.notify.created}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    @Override
    public void publish(NotifyMessage message) {
        // 事件是在主事务里发的：等提交后再投递，回滚就不发
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(message);
                }
            });
            return;
        }
        send(message);
    }

    private void send(NotifyMessage message) {
        rabbitTemplate.convertAndSend(exchange, routingKey, message);
        log.debug("notify.mq.published type={} recipient={} source={}",
                message.type(), message.recipientId(), message.sourceId());
    }
}
