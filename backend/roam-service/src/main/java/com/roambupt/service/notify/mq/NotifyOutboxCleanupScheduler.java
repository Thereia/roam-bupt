package com.roambupt.service.notify.mq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每天凌晨清理 7 天前已发送的 outbox 行。 */
@Component
@ConditionalOnProperty(name = "roam.notify.mq.enabled", havingValue = "true")
public class NotifyOutboxCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotifyOutboxCleanupScheduler.class);
    private static final int KEEP_DAYS = 7;

    private final NotifyOutboxService outboxService;

    public NotifyOutboxCleanupScheduler(NotifyOutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Scheduled(cron = "0 30 3 * * *")
    public void cleanup() {
        int deleted = outboxService.cleanupSent(KEEP_DAYS);
        if (deleted > 0) {
            log.info("notify.outbox.cleanup deleted={}", deleted);
        }
    }
}
