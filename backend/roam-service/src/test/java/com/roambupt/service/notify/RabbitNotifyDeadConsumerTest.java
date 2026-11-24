package com.roambupt.service.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.roambupt.service.notify.mq.NotifyMessage;
import com.roambupt.service.notify.mq.NotifyMqSender;
import com.roambupt.service.notify.mq.RabbitNotifyDeadConsumer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RabbitNotifyDeadConsumerTest {

    private final NotifyMqSender sender = mock(NotifyMqSender.class);
    private final Channel channel = mock(Channel.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RabbitNotifyDeadConsumer consumer =
            new RabbitNotifyDeadConsumer(objectMapper, sender, new SimpleMeterRegistry());

    private final NotifyMessage payload = new NotifyMessage(
            2L, 3L, "COMMENT", "COMMENT", 9L, 7L, "评论了你的帖子");

    private Message messageWithDeaths(int count) throws Exception {
        MessageProperties props = new MessageProperties();
        if (count > 0) {
            props.setHeader("x-death", List.of(Map.of("count", count, "queue", "roam.notify.queue")));
        }
        return new Message(objectMapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8), props);
    }

    @Test
    void 死信未超上限_重投并ack() throws Exception {
        consumer.onDead(messageWithDeaths(1), channel, 21L);

        verify(sender).send(any(NotifyMessage.class), eq(null));
        verify(channel).basicAck(21L, false);
    }

    @Test
    void 死信超过上限_丢弃并ack() throws Exception {
        consumer.onDead(messageWithDeaths(3), channel, 22L);

        verify(sender, never()).send(any(NotifyMessage.class), any());
        verify(channel).basicAck(22L, false);
    }

    @Test
    void 消息体坏了_nack不重回队列() throws Exception {
        Message bad = new Message("not-json".getBytes(StandardCharsets.UTF_8), new MessageProperties());

        consumer.onDead(bad, channel, 23L);

        verify(channel).basicNack(23L, false, false);
        verify(channel, never()).basicAck(eq(23L), anyBoolean());
    }
}
