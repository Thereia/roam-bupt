package com.roambupt.service.search;

import com.roambupt.api.model.event.PostStatusChangedEvent;
import com.roambupt.api.model.event.PostUpdatedEvent;
import com.roambupt.service.search.index.SearchIndexService;
import com.roambupt.service.search.listener.SearchIndexListener;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SearchIndexListenerTest {

    private final SearchIndexService indexService = mock(SearchIndexService.class);
    private final SearchIndexListener listener = new SearchIndexListener(indexService);

    @Test
    void 编辑帖子_整篇重建索引() {
        listener.onPostUpdated(new PostUpdatedEvent(9L));

        verify(indexService).index(9L);
    }

    @Test
    void 状态变化_只改状态不读正文() {
        listener.onPostStatusChanged(new PostStatusChangedEvent(9L, "OFFLINE"));

        verify(indexService).updateStatus(9L, "OFFLINE");
    }
}
