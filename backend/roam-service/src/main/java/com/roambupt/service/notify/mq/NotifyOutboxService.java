package com.roambupt.service.notify.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roambupt.service.notify.repository.entity.NotifyOutboxDO;
import com.roambupt.service.notify.repository.mapper.NotifyOutboxMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通知失败暂存与重投。
 *
 * <p>发送失败或发布确认 nack 时把消息写进 notify_outbox，定时任务按 next_retry_time 重投；
 * 超过最大次数标记 DEAD，留待人工处理。</p>
 */
@Service
public class NotifyOutboxService {

    private static final Logger log = LoggerFactory.getLogger(NotifyOutboxService.class);

    static final String STATUS_PENDING = "PENDING";
    static final String STATUS_SENT = "SENT";
    static final String STATUS_DEAD = "DEAD";
    static final int MAX_ATTEMPTS = 5;

    private final NotifyOutboxMapper outboxMapper;
    private final ObjectMapper objectMapper;

    public NotifyOutboxService(NotifyOutboxMapper outboxMapper, ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.objectMapper = objectMapper;
    }

    /** 落库一条待重投消息；序列化失败只记日志，不让主流程抛错。 */
    public void save(NotifyMessage message) {
        try {
            LocalDateTime now = LocalDateTime.now();
            NotifyOutboxDO row = new NotifyOutboxDO();
            row.setPayload(objectMapper.writeValueAsString(message));
            row.setStatus(STATUS_PENDING);
            row.setAttempts(0);
            row.setNextRetryTime(now.plusSeconds(30));
            row.setCreatedTime(now);
            row.setUpdatedTime(now);
            outboxMapper.insert(row);
            log.warn("notify.outbox.saved type={} recipient={} source={}",
                    message.type(), message.recipientId(), message.sourceId());
        } catch (Exception e) {
            log.error("notify.outbox.save.failed type={} reason={}", message.type(), e.getMessage());
        }
    }

    /** 到期待重投的消息，按时间升序取一批。 */
    public List<NotifyOutboxDO> findDue(int limit) {
        return outboxMapper.selectList(Wrappers.<NotifyOutboxDO>lambdaQuery()
                .eq(NotifyOutboxDO::getStatus, STATUS_PENDING)
                .le(NotifyOutboxDO::getNextRetryTime, LocalDateTime.now())
                .orderByAsc(NotifyOutboxDO::getNextRetryTime)
                .last("LIMIT " + Math.max(1, limit)));
    }

    @Transactional(rollbackFor = Exception.class)
    public void markSent(NotifyOutboxDO row) {
        NotifyOutboxDO update = new NotifyOutboxDO();
        update.setId(row.getId());
        update.setStatus(STATUS_SENT);
        update.setUpdatedTime(LocalDateTime.now());
        outboxMapper.updateById(update);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFailed(NotifyOutboxDO row) {
        int attempts = (row.getAttempts() == null ? 0 : row.getAttempts()) + 1;
        NotifyOutboxDO update = new NotifyOutboxDO();
        update.setId(row.getId());
        update.setAttempts(attempts);
        update.setStatus(attempts >= MAX_ATTEMPTS ? STATUS_DEAD : STATUS_PENDING);
        update.setNextRetryTime(LocalDateTime.now().plusSeconds(30L * attempts));
        update.setUpdatedTime(LocalDateTime.now());
        outboxMapper.updateById(update);
    }

    public void markSentById(Long id) {
        NotifyOutboxDO row = outboxMapper.selectById(id);
        if (row != null) {
            markSent(row);
        }
    }

    public void markFailedById(Long id) {
        NotifyOutboxDO row = outboxMapper.selectById(id);
        if (row != null) {
            markFailed(row);
        }
    }

    /** 清理 7 天前已发送的行；DEAD 保留人工排查。 */
    public int cleanupSent(int keepDays) {
        return outboxMapper.deleteSentBefore(LocalDateTime.now().minusDays(Math.max(1, keepDays)));
    }

    public NotifyMessage deserialize(String payload) throws Exception {
        return objectMapper.readValue(payload, NotifyMessage.class);
    }
}
