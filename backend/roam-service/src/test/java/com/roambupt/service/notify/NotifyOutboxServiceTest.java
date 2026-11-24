package com.roambupt.service.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roambupt.service.notify.mq.NotifyMessage;
import com.roambupt.service.notify.mq.NotifyOutboxService;
import com.roambupt.service.notify.repository.entity.NotifyOutboxDO;
import com.roambupt.service.notify.repository.mapper.NotifyOutboxMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotifyOutboxServiceTest {

    private final NotifyOutboxMapper mapper = mock(NotifyOutboxMapper.class);
    private final NotifyOutboxService service = new NotifyOutboxService(mapper, new ObjectMapper());

    private final NotifyMessage message = new NotifyMessage(
            2L, 3L, "COMMENT", "COMMENT", 9L, 7L, "评论了你的帖子");

    @Test
    void 失败消息_落库为待重投() {
        service.save(message);

        ArgumentCaptor<NotifyOutboxDO> captor = ArgumentCaptor.forClass(NotifyOutboxDO.class);
        verify(mapper).insert(captor.capture());
        NotifyOutboxDO row = captor.getValue();
        assertThat(row.getStatus()).isEqualTo("PENDING");
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getPayload()).contains("\"type\":\"COMMENT\"");
        assertThat(row.getNextRetryTime()).isNotNull();
    }

    @Test
    void 重投失败_次数加一并延后() {
        NotifyOutboxDO row = new NotifyOutboxDO();
        row.setId(1L);
        row.setAttempts(1);

        service.markFailed(row);

        ArgumentCaptor<NotifyOutboxDO> captor = ArgumentCaptor.forClass(NotifyOutboxDO.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getAttempts()).isEqualTo(2);
        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void 达到上限_标记DEAD() {
        NotifyOutboxDO row = new NotifyOutboxDO();
        row.setId(1L);
        row.setAttempts(4);

        service.markFailed(row);

        ArgumentCaptor<NotifyOutboxDO> captor = ArgumentCaptor.forClass(NotifyOutboxDO.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getAttempts()).isEqualTo(5);
        assertThat(captor.getValue().getStatus()).isEqualTo("DEAD");
    }

    @Test
    void 清理已发送历史_按天数删() {
        org.mockito.Mockito.when(mapper.deleteSentBefore(org.mockito.ArgumentMatchers.any()))
                .thenReturn(3);

        int deleted = service.cleanupSent(7);

        assertThat(deleted).isEqualTo(3);
        verify(mapper).deleteSentBefore(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 反序列化_还原消息() throws Exception {
        String payload = new ObjectMapper().writeValueAsString(message);
        NotifyMessage parsed = service.deserialize(payload);
        assertThat(parsed.recipientId()).isEqualTo(2L);
        assertThat(parsed.content()).isEqualTo("评论了你的帖子");
    }
}
