package com.roambupt.service.comment.service;

/** 评论读能力，供其他域使用（通知找被回复的人）。 */
public interface CommentQueryService {

    Long findAuthorId(long commentId);
}
