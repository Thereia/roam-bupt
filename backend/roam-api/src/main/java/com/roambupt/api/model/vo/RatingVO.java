package com.roambupt.api.model.vo;

/** 地点评分。P0 无评分表，全为 0。 */
public record RatingVO(double avg, double displayAvg, long count) {
}
