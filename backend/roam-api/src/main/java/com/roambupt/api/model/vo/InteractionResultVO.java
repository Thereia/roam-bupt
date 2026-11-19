package com.roambupt.api.model.vo;

/** 点赞/收藏结果：active 表示当前是否已点赞/已收藏。 */
public record InteractionResultVO(boolean active, long count) {
}
