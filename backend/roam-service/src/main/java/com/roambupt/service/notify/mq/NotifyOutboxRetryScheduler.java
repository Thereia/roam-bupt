package com.roambupt.service.notify.mq;

import com.roambupt.service.notify.config.NotifyMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 通知重投：把 notify_outbox 里到期的消息重新投递。
 *
 * <p>发送成功与否由 {@link NotifyMqSender} 的发布确认决定，确认 ack 才标记 SENT；
 * nack / return / 超时会把这一行标记失败并延后重试。</p>
 */
@Component
@ConditionalOnProperty(name = "roam.notify.mq.enabled", havingValue = "true")
public class NotifyOutboxRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotifyOutboxRetryScheduler.class);
    private static final int BATCH = 20;

    private final NotifyOutboxService outboxService;
    private final NotifyMqSender sender;

    public NotifyOutboxRetryScheduler(NotifyOutboxService outboxService,
                                      NotifyMqSender sender,
                                      NotifyMqProperties properties) {
        this.outboxService = outboxService;
        this.sender = sender;
    }

    @Scheduled(fixedDelayString = "${roam.notify.mq.retry-interval-ms:30000}",
            initialDelayString = "${roam.notify.mq.retry-initial-delay-ms:30000}")
    public void retryDue() {
        for (var row : outboxService.findDue(BATCH)) {
            try {
                NotifyMessage message = outboxService.deserialize(row.getPayload());
                sender.send(message, row.getId());
                log.info("notify.outbox.resent id={} type={}", row.getId(), message.type());
            } catch (Exception e) {
                outboxService.markFailed(row);
                log.warn("notify.outbox.retry.failed id={} attempts={} reason={}",
                        row.getId(), row.getAttempts(), e.getMessage());
            }
        }
    }
}
