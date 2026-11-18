package com.roambupt.api.model.dto;

/** 主题列表查询参数。 */
public record TopicQuery(String type, String category, String q, int page, int pageSize) {
}
