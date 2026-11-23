package com.roambupt.service.notify.mq;

import com.roambupt.service.notify.service.NotificationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * 通知分发：有 MQ 发布器就投递，没有就同步写库。
 *
 * <p>这样本地不开 RabbitMQ 也能跑，生产打开配置就走异步。</p>
 */
@Component
public class NotifyDispatcher {

    private final NotificationService notificationService;
    private final ObjectProvider<NotifyPublisher> publisher;

    public NotifyDispatcher(NotificationService notificationService,
                            ObjectProvider<NotifyPublisher> publisher) {
        this.notificationService = notificationService;
        this.publisher = publisher;
    }

    public void dispatch(NotifyMessage message) {
        NotifyPublisher mq = publisher.getIfAvailable();
        if (mq != null) {
            mq.publish(message);
            return;
        }
        notificationService.send(message.recipientId(), message.actorId(), message.type(),
                message.sourceType(), message.sourceId(), message.postId(), message.content());
    }
}
