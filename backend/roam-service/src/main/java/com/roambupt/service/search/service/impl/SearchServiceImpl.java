package com.roambupt.service.search.service.impl;

import com.roambupt.api.model.dto.SearchQuery;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.PostVO;
import com.roambupt.service.post.service.PostQueryService;
import com.roambupt.service.post.service.PostReadService;
import com.roambupt.service.search.config.SearchProperties;
import com.roambupt.service.search.embedding.EmbeddingClient;
import com.roambupt.service.search.service.SearchService;
import com.roambupt.service.search.support.EsSearchGateway;
import com.roambupt.service.search.support.RrfFusion;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索实现：ES 语义 + 关键词双路召回，RRF 融合；ES 异常或不可用时降级 MySQL。
 */
@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_KEYWORD_LENGTH = 50;
    /** 单路最多取多少条参与融合；融合在内存里做，超出部分不分页。 */
    private static final int FUSION_WINDOW = 50;
    private static final int RRF_K = 60;
    /** 语义兜底最多返回多少条。 */
    private static final int SEMANTIC_FALLBACK = 12;

    private final SearchProperties properties;
    private final EsSearchGateway esSearchGateway;
    private final ObjectProvider<EmbeddingClient> embeddingClientProvider;
    private final PostQueryService postQueryService;
    private final PostReadService postReadService;

    public SearchServiceImpl(SearchProperties properties,
                             EsSearchGateway esSearchGateway,
                             ObjectProvider<EmbeddingClient> embeddingClientProvider,
                             PostQueryService postQueryService,
                             PostReadService postReadService) {
        this.properties = properties;
        this.esSearchGateway = esSearchGateway;
        this.embeddingClientProvider = embeddingClientProvider;
        this.postQueryService = postQueryService;
        this.postReadService = postReadService;
    }

    @Override
    public PageResult<PostVO> search(SearchQuery query, Long viewerUid) {
        String keyword = StringUtils.trimToEmpty(query.keyword());
        if (keyword.isEmpty()) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "搜索关键词不能为空");
        }
        if (keyword.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "关键词最多 50 字");
        }
        int page = Math.max(1, query.page());
        int size = Math.min(Math.max(1, query.pageSize()), MAX_PAGE_SIZE);
        int offset = (page - 1) * size;

        if (properties.enabled()) {
            try {
                PageResult<PostVO> result = hybridSearch(keyword, query.topicId(), page, size, offset, viewerUid);
                if (result != null) {
                    return result;
                }
            } catch (Exception e) {
                log.warn("search.es.fallback keyword={} reason={}", keyword, e.getMessage());
            }
        }

        long total = postQueryService.countSearch(keyword, query.topicId());
        List<Long> ids = postQueryService.searchIds(keyword, query.topicId(), offset, size);
        return PageResult.of(total, page, size, postReadService.byIds(ids, viewerUid));
    }

    /** 两路召回 + RRF；两路都空返回 null，交给 MySQL 兜底。 */
    private PageResult<PostVO> hybridSearch(String keyword, Long topicId, int page, int size,
                                            int offset, Long viewerUid) throws Exception {
        List<Long> keywordIds = new ArrayList<>();
        EsSearchGateway.SearchHits bm25 =
                esSearchGateway.search(keyword, topicId, 0, FUSION_WINDOW);
        if (bm25 != null) {
            keywordIds = bm25.ids();
        }

        List<Long> vectorIds = new ArrayList<>();
        EmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
        if (embeddingClient != null) {
            try {
                float[] vector = embeddingClient.embed(keyword);
                vectorIds = esSearchGateway.knnSearch(vector, topicId, FUSION_WINDOW);
            } catch (Exception e) {
                log.warn("search.vector.fallback keyword={} reason={}", keyword, e.getMessage());
            }
        }

        if (keywordIds.isEmpty() && vectorIds.isEmpty()) {
            return null;
        }
        List<Long> fused;
        if (keywordIds.isEmpty()) {
            // 关键词完全没命中：向量兜底，取最相近的一批，不把整库都倒出来
            fused = vectorIds.subList(0, Math.min(SEMANTIC_FALLBACK, vectorIds.size()));
        } else {
            // 有关键词命中：向量只在这个候选集内做重排，不把整库都算成结果
            java.util.Set<Long> candidates = new java.util.HashSet<>(keywordIds);
            List<Long> rerank = vectorIds.stream().filter(candidates::contains).toList();
            fused = RrfFusion.fuse(keywordIds, rerank, RRF_K);
        }
        int from = Math.min(offset, fused.size());
        int to = Math.min(offset + size, fused.size());
        List<Long> pageIds = fused.subList(from, to);
        return PageResult.of(fused.size(), page, size, postReadService.byIds(pageIds, viewerUid));
    }
}
