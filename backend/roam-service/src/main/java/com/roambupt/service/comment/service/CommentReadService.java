package com.roambupt.service.comment.service;

import com.roambupt.api.model.vo.CommentVO;
import com.roambupt.api.model.vo.PageResult;

/** 评论读服务。 */
public interface CommentReadService {

    PageResult<CommentVO> listTop(long postId, int page, int pageSize);

    PageResult<CommentVO> listReplies(long topCommentId, int page, int pageSize);
}
