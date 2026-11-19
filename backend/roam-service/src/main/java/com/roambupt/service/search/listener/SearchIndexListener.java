package com.roambupt.service.search.listener;

import com.roambupt.api.model.event.PostCreatedEvent;
import com.roambupt.service.search.index.SearchIndexService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 搜索索引同步。
 *
 * <p>帖子创建后索引（提交后执行，ES 失败不影响发帖）；应用启动后做一次全量重建。</p>
 */
@Component
public class SearchIndexListener {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexListener.class);

    private final SearchIndexService searchIndexService;

    public SearchIndexListener(SearchIndexService searchIndexService) {
        this.searchIndexService = searchIndexService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPostCreated(PostCreatedEvent event) {
        try {
            searchIndexService.index(event.postId());
        } catch (Exception e) {
            log.warn("search.sync.failed postId={} reason={}", event.postId(), e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        try {
            searchIndexService.rebuildAll();
        } catch (Exception e) {
            log.warn("search.rebuild.failed reason={}", e.getMessage());
        }
    }
}
