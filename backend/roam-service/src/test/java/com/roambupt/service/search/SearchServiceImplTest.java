package com.roambupt.service.search;

import com.roambupt.api.model.dto.SearchQuery;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.PostVO;
import com.roambupt.service.post.service.PostQueryService;
import com.roambupt.service.post.service.PostReadService;
import com.roambupt.service.search.config.SearchProperties;
import com.roambupt.service.search.embedding.EmbeddingClient;
import com.roambupt.service.search.service.impl.SearchServiceImpl;
import com.roambupt.service.search.support.EsSearchGateway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchServiceImplTest {

    private final EsSearchGateway gateway = mock(EsSearchGateway.class);
    private final PostQueryService postQueryService = mock(PostQueryService.class);
    private final PostReadService postReadService = mock(PostReadService.class);
    private final EmbeddingClient embeddingClient = mock(EmbeddingClient.class);

    @SuppressWarnings("unchecked")
    private final ObjectProvider<EmbeddingClient> provider = mock(ObjectProvider.class);

    private final SearchProperties properties = new SearchProperties(true, "h", 9200, "idx", 1000, 2000,
            new SearchProperties.Vector(true, "e", "k", "m", 3, 1000, 50, 500, 0.7, 12));

    private SearchServiceImpl service() {
        when(provider.getIfAvailable()).thenReturn(embeddingClient);
        when(embeddingClient.embed(any())).thenReturn(new float[]{0.1f, 0.2f, 0.3f});
        when(postReadService.byIds(any(), any())).thenAnswer(inv ->
                ((List<Long>) inv.getArgument(0)).stream()
                        .map(id -> new PostVO(id, "t" + id, "", "", "ONLINE", null, List.of(), List.of(),
                                null, null, null, null, null))
                        .toList());
        return new SearchServiceImpl(properties, gateway, provider, postQueryService, postReadService);
    }

    @Test
    void 关键词命中时_语义只参与重排不灌入结果() throws Exception {
        when(gateway.search(any(), any(), eq(0), anyInt()))
                .thenReturn(new EsSearchGateway.SearchHits(List.of(1L, 2L), 2));
        when(gateway.knnSearch(any(), any(), anyInt())).thenReturn(List.of(2L, 3L, 4L));

        PageResult<PostVO> result = service().search(new SearchQuery("教三", null, 1, 10), null);

        // 向量把 2 提到前面，但结果里不能出现 3、4
        assertThat(result.list()).extracting(PostVO::id).containsExactlyInAnyOrder(1L, 2L);
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    void 关键词零命中_语义兜底并截断() throws Exception {
        when(gateway.search(any(), any(), eq(0), anyInt()))
                .thenReturn(new EsSearchGateway.SearchHits(List.of(), 0));
        when(gateway.knnSearch(any(), any(), anyInt())).thenReturn(List.of(5L, 6L));

        PageResult<PostVO> result = service().search(new SearchQuery("安静看书", null, 1, 10), null);

        assertThat(result.list()).extracting(PostVO::id).containsExactly(5L, 6L);
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    void 深翻页超出窗口_退化成纯关键词页() throws Exception {
        when(gateway.search(any(), any(), eq(0), anyInt()))
                .thenReturn(new EsSearchGateway.SearchHits(List.of(1L), 100));
        when(gateway.knnSearch(any(), any(), anyInt())).thenReturn(List.of(1L));
        when(gateway.search(any(), any(), eq(90), anyInt()))
                .thenReturn(new EsSearchGateway.SearchHits(List.of(77L), 100));

        PageResult<PostVO> result = service().search(new SearchQuery("教三", null, 10, 10), null);

        assertThat(result.list()).extracting(PostVO::id).containsExactly(77L);
        assertThat(result.total()).isEqualTo(100);
        verify(gateway).search(any(), any(), eq(90), anyInt());
    }
}
