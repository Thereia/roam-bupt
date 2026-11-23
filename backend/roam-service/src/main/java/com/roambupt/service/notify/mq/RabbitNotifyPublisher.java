package com.roambupt.service.notify.mq;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** RabbitMQ 通知发布器：只负责把消息交给可靠投递组件。 */
@Component
@ConditionalOnProperty(name = "roam.notify.mq.enabled", havingValue = "true")
public class RabbitNotifyPublisher implements NotifyPublisher {

    private final NotifyMqSender sender;

    public RabbitNotifyPublisher(NotifyMqSender sender) {
        this.sender = sender;
    }

    @Override
    public void publish(NotifyMessage message) {
        sender.publish(message);
    }
}
