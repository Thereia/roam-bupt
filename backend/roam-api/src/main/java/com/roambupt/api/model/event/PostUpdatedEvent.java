package com.roambupt.api.model.event;

/** 帖子内容被编辑（标题/正文/媒体/地点）。 */
public record PostUpdatedEvent(long postId) {
}
