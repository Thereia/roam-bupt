package com.roambupt.api.model.dto;

/** 搜索入参：关键词 + 可选地点过滤。 */
public record SearchQuery(String keyword, Long topicId, int page, int pageSize) {
}
