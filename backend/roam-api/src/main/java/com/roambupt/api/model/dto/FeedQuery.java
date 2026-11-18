package com.roambupt.api.model.dto;

/** 信息流查询参数。feed 取 recommend / latest / following。 */
public record FeedQuery(String feed, Long topicId, int page, int pageSize) {
}
