package com.roambupt.service.search.service;

import com.roambupt.api.model.dto.SearchQuery;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.PostVO;

/** 帖子搜索。 */
public interface SearchService {

    PageResult<PostVO> search(SearchQuery query, Long viewerUid);
}
