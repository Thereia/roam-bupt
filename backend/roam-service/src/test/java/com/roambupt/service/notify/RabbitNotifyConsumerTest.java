package com.roambupt.service.notify;

import com.rabbitmq.client.Channel;
import com.roambupt.service.notify.mq.NotifyMessage;
import com.roambupt.service.notify.mq.RabbitNotifyConsumer;
import com.roambupt.service.notify.service.NotificationService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitNotifyConsumerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final Channel channel = mock(Channel.class);
    private final RabbitNotifyConsumer consumer = new RabbitNotifyConsumer(notificationService);
    private final NotifyMessage message = new NotifyMessage(
            2L, 3L, "LIKE", "POST", 7L, 7L, "赞了你的帖子");

    @Test
    void 成功消费_ack() throws Exception {
        consumer.onMessage(message, channel, 11L);

        verify(notificationService).send(2L, 3L, "LIKE", "POST", 7L, 7L, "赞了你的帖子");
        verify(channel).basicAck(11L, false);
    }

    @Test
    void 消费失败_拒绝进死信() throws Exception {
        doThrow(new RuntimeException("boom"))
                .when(notificationService)
                .send(any(Long.class), any(Long.class), anyString(), anyString(),
                        any(Long.class), any(), anyString());

        consumer.onMessage(message, channel, 12L);

        verify(channel).basicNack(12L, false, false);
    }
}
