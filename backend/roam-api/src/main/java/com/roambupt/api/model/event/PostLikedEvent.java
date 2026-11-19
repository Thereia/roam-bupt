package com.roambupt.api.model.event;

/** 点赞事件，供通知域消费。action 为 LIKE / COLLECT。 */
public record PostLikedEvent(Long postId, Long uid, String action) {
}
