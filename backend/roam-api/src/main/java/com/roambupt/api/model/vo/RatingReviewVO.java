package com.roambupt.api.model.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 一条地点评价：星 + 文字 + 图 + 作者。 */
public record RatingReviewVO(long id, UserBriefVO author, int score, String review,
                             List<String> images, LocalDateTime createdTime,
                             LocalDateTime updatedTime, boolean mine) {
}
