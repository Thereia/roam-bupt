package com.roambupt.api.model.event;

/** 帖子创建事件，供 P1 搜索索引消费。纯数据。 */
public record PostCreatedEvent(Long postId, Long uid) {
}
