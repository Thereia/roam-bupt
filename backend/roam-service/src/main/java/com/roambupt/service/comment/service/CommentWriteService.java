package com.roambupt.service.comment.service;

import com.roambupt.api.model.dto.CommentCreateCommand;
import com.roambupt.api.model.vo.CommentCreatedVO;

/** 评论写服务。 */
public interface CommentWriteService {

    CommentCreatedVO create(long postId, CommentCreateCommand command, long uid);

    void delete(long commentId, long uid, String role);
}
