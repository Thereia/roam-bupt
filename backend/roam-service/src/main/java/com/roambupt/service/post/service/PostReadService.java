package com.roambupt.service.post.service;

import com.roambupt.api.model.dto.FeedQuery;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.PostVO;

/** 帖子读服务。 */
public interface PostReadService {

    PostVO detail(long postId, Long viewerUid, String viewerRole);

    PageResult<PostVO> feed(FeedQuery query, Long viewerUid);

    PageResult<PostVO> byTopic(long topicId, String sort, int page, int pageSize);

    /** 按 id 批量取（搜索结果回读），保持传入顺序。 */
    java.util.List<PostVO> byIds(java.util.List<Long> ids, Long viewerUid);
}
