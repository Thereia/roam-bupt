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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 搜索实现：BM25 + 向量两路召回，应用层加权 RRF 融合；ES 异常或不可用时降级 MySQL。
 *
 * <p>分页口径照 taco：两路都从第 1 名取到"本页末尾"（{@code min(window, offset+size)}），
 * 融合后切片，避免逐页取导致同一文档跨页名次漂移；total 用关键词腿的真实命中数。</p>
 */
@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_KEYWORD_LENGTH = 50;
    private static final int RRF_K = 60;

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
        SearchProperties.Vector vectorConfig = properties.vector();
        int window = vectorConfig == null ? 50 : Math.max(1, vectorConfig.fusionWindow());
        int need = Math.min(window, offset + size);

        EsSearchGateway.SearchHits bm25 = esSearchGateway.search(keyword, topicId, 0, need);
        List<Long> keywordIds = bm25 == null ? List.of() : bm25.ids();
        long keywordTotal = bm25 == null ? 0 : bm25.total();

        int semanticCap = vectorConfig == null ? 12 : Math.max(1, vectorConfig.semanticFallback());
        List<Long> vectorIds = List.of();
        EmbeddingClient embeddingClient = embeddingClientProvider.getIfAvailable();
        if (embeddingClient != null) {
            try {
                float[] vector = embeddingClient.embed(keyword);
                // 即使第一页也要取够兜底条数，否则 total 会随页数变化
                vectorIds = esSearchGateway.knnSearch(vector, topicId, Math.max(need, semanticCap));
            } catch (Exception e) {
                log.warn("search.vector.fallback keyword={} reason={}", keyword, e.getMessage());
            }
        }

        if (keywordIds.isEmpty() && vectorIds.isEmpty()) {
            return null;
        }

        if (keywordIds.isEmpty()) {
            List<Long> fallback = vectorIds.subList(0, Math.min(semanticCap, vectorIds.size()));
            int from = Math.min(offset, fallback.size());
            int to = Math.min(offset + size, fallback.size());
            return PageResult.of(fallback.size(), page, size,
                    postReadService.byIds(fallback.subList(from, to), viewerUid));
        }

        // 有关键词命中：语义只做重排，不把整库灌进结果
        Set<Long> candidates = new HashSet<>(keywordIds);
        List<Long> rerankable = new ArrayList<>();
        for (Long id : vectorIds) {
            if (candidates.contains(id)) {
                rerankable.add(id);
            }
        }
        double semanticWeight = vectorConfig == null ? 0.7 : vectorConfig.semanticWeight();
        List<Long> fused = RrfFusion.fuse(RRF_K, new double[]{1.0, semanticWeight}, keywordIds, rerankable);

        if (offset >= fused.size()) {
            // 深翻页超出融合窗口：退化成纯 BM25 的对应页，而不是空页
            if (keywordTotal <= offset) {
                return PageResult.of(keywordTotal, page, size, List.of());
            }
            EsSearchGateway.SearchHits deep = esSearchGateway.search(keyword, topicId, offset, size);
            int deepTo = deep == null ? 0 : Math.min(size, deep.ids().size());
            List<Long> deepIds = deep == null ? List.of() : deep.ids().subList(0, deepTo);
            return PageResult.of(keywordTotal, page, size, postReadService.byIds(deepIds, viewerUid));
        }

        int from = Math.min(offset, fused.size());
        int to = Math.min(offset + size, fused.size());
        return PageResult.of(keywordTotal, page, size,
                postReadService.byIds(fused.subList(from, to), viewerUid));
    }
}
