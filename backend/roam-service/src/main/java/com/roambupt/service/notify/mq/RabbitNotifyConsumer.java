package com.roambupt.service.notify.mq;

import com.roambupt.service.notify.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 通知消费者：把消息写成通知。
 *
 * <p>幂等靠 notification 表的 uk_notify 唯一键：重复投递时捕获 DuplicateKeyException 当成功处理；
 * 其他异常抛出，由 listener 重试，超过次数后进死信队列。</p>
 */
@Component
@ConditionalOnProperty(name = "roam.notify.mq.enabled", havingValue = "true")
public class RabbitNotifyConsumer {

    private static final Logger log = LoggerFactory.getLogger(RabbitNotifyConsumer.class);

    private final NotificationService notificationService;

    public RabbitNotifyConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @RabbitListener(queues = "${roam.notify.mq.queue:roam.notify.queue}")
    public void onMessage(NotifyMessage message) {
        try {
            notificationService.send(message.recipientId(), message.actorId(), message.type(),
                    message.sourceType(), message.sourceId(), message.postId(), message.content());
        } catch (DuplicateKeyException e) {
            // 同一事件重复投递，按成功处理
            log.debug("notify.mq.duplicate type={} recipient={} source={}",
                    message.type(), message.recipientId(), message.sourceId());
        }
    }
}
