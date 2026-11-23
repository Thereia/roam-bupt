package com.roambupt.service.notify;

import com.roambupt.service.notify.mq.NotifyMessage;
import com.roambupt.service.notify.mq.RabbitNotifyConsumer;
import com.roambupt.service.notify.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RabbitNotifyConsumerTest {

    private final NotificationService notificationService = mock(NotificationService.class);
    private final RabbitNotifyConsumer consumer = new RabbitNotifyConsumer(notificationService);

    private final NotifyMessage message = new NotifyMessage(
            2L, 3L, "LIKE", "POST", 7L, 7L, "赞了你的帖子");

    @Test
    void 消息_写成通知() {
        consumer.onMessage(message);

        verify(notificationService).send(2L, 3L, "LIKE", "POST", 7L, 7L, "赞了你的帖子");
    }

    @Test
    void 重复投递_按成功处理() {
        doThrow(new DuplicateKeyException("dup"))
                .when(notificationService)
                .send(any(Long.class), any(Long.class), anyString(), anyString(),
                        any(Long.class), any(), anyString());

        assertThatCode(() -> consumer.onMessage(message)).doesNotThrowAnyException();
    }
}
