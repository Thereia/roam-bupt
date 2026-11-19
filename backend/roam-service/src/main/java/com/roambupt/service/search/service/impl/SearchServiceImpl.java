package com.roambupt.service.search.service.impl;

import com.roambupt.api.model.dto.SearchQuery;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.PostVO;
import com.roambupt.service.post.service.PostQueryService;
import com.roambupt.service.post.service.PostReadService;
import com.roambupt.service.search.config.SearchProperties;
import com.roambupt.service.search.service.SearchService;
import com.roambupt.service.search.support.EsSearchGateway;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** 搜索实现：ES 召回优先，异常或关闭时降级 MySQL。 */
@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_KEYWORD_LENGTH = 50;

    private final SearchProperties properties;
    private final EsSearchGateway esSearchGateway;
    private final PostQueryService postQueryService;
    private final PostReadService postReadService;

    public SearchServiceImpl(SearchProperties properties,
                             EsSearchGateway esSearchGateway,
                             PostQueryService postQueryService,
                             PostReadService postReadService) {
        this.properties = properties;
        this.esSearchGateway = esSearchGateway;
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
                EsSearchGateway.SearchHits hits =
                        esSearchGateway.search(keyword, query.topicId(), offset, size);
                if (hits != null) {
                    List<PostVO> list = postReadService.byIds(hits.ids(), viewerUid);
                    return PageResult.of(hits.total(), page, size, list);
                }
            } catch (Exception e) {
                log.warn("search.es.fallback keyword={} reason={}", keyword, e.getMessage());
            }
        }

        long total = postQueryService.countSearch(keyword, query.topicId());
        List<Long> ids = postQueryService.searchIds(keyword, query.topicId(), offset, size);
        return PageResult.of(total, page, size, postReadService.byIds(ids, viewerUid));
    }
}
