package com.roambupt.service.notify;

import com.roambupt.service.notify.mq.NotifyDispatcher;
import com.roambupt.service.notify.mq.NotifyMessage;
import com.roambupt.service.notify.mq.NotifyPublisher;
import com.roambupt.service.notify.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotifyDispatcherTest {

    private final NotificationService notificationService = mock(NotificationService.class);

    private final NotifyMessage message = new NotifyMessage(
            2L, 3L, "COMMENT", "COMMENT", 9L, 7L, "评论了你的帖子");

    @Test
    void 开了MQ_投递而不是直接写库() {
        NotifyPublisher publisher = mock(NotifyPublisher.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<NotifyPublisher> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(publisher);

        new NotifyDispatcher(notificationService, provider).dispatch(message);

        verify(publisher).publish(message);
        verify(notificationService, never()).send(any(Long.class), any(Long.class), any(),
                any(), any(Long.class), any(), any());
    }

    @Test
    void 没开MQ_同步写库() {
        @SuppressWarnings("unchecked")
        ObjectProvider<NotifyPublisher> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        new NotifyDispatcher(notificationService, provider).dispatch(message);

        verify(notificationService).send(2L, 3L, "COMMENT", "COMMENT", 9L, 7L, "评论了你的帖子");
    }
}
