package com.roambupt.api.model.vo;

/** 评分汇总：均分已做贝叶斯平滑；distribution 为 1..5 星各几条。 */
public record RatingSummaryVO(long topicId, long count, double average,
                              Integer myScore, int[] distribution) {
}
