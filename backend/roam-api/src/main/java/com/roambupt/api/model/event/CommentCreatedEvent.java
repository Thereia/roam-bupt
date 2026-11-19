package com.roambupt.api.model.event;

/** 评论创建事件，供 P1 通知域消费。纯数据。 */
public record CommentCreatedEvent(Long postId, Long commentId, Long uid, Long parentId) {
}
