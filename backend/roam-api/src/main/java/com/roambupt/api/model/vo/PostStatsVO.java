package com.roambupt.api.model.vo;

import java.util.List;

/** 侧栏「逛什么」用的帖子数：总数 + 每个分类标签下的在线帖数。 */
public record PostStatsVO(long total, List<CategoryCountVO> categories) {
}
