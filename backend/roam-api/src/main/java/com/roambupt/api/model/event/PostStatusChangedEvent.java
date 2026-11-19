package com.roambupt.api.model.event;

/** 帖子状态变化，供搜索索引同步。 */
public record PostStatusChangedEvent(Long postId, String status) {
}
