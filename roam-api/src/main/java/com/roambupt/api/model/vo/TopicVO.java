package com.roambupt.api.model.vo;

/** 主题（地点/分类）视图。 */
public record TopicVO(Long id, String type, String name, String category, String cover,
                      String description, Integer heat, Long nodeId,
                      long postCount, long todayRouteCount, RatingVO rating) {
}
