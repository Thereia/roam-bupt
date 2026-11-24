package com.roambupt.service.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roambupt.service.notify.config.NotifyMqProperties;
import com.roambupt.service.notify.mq.NotifyMessage;
import com.roambupt.service.notify.mq.NotifyMqSender;
import com.roambupt.service.notify.mq.NotifyOutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class NotifyMqSenderTest {

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final NotifyOutboxService outboxService = mock(NotifyOutboxService.class);
    private final NotifyMqProperties properties = new NotifyMqProperties();
    private final NotifyMqSender sender =
            new NotifyMqSender(rabbitTemplate, outboxService, new ObjectMapper(), properties, new SimpleMeterRegistry());
    private final NotifyMessage message = new NotifyMessage(
            2L, 3L, "COMMENT", "COMMENT", 9L, 7L, "评论了你的帖子");

    @Test
    void 发送异常_落库待重投() {
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        sender.send(message, null);

        verify(outboxService).save(message);
    }

    @Test
    void 重投发送异常_标记失败而不是再落一条() {
        doThrow(new AmqpException("broker down"))
                .when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        sender.send(message, 42L);

        verify(outboxService).markFailedById(42L);
        verify(outboxService, never()).save(any(NotifyMessage.class));
    }
}
