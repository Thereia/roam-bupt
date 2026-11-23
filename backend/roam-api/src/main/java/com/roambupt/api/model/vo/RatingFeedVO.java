package com.roambupt.api.model.vo;

/** 评价流：顶部汇总 + 分页评价列表。 */
public record RatingFeedVO(RatingSummaryVO summary, PageResult<RatingReviewVO> reviews) {
}
