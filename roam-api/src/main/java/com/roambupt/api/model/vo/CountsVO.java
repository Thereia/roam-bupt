package com.roambupt.api.model.vo;

/** 帖子互动计数。P0 全为 0（互动在 P1）。 */
public record CountsVO(long like, long collect, long comment, long view) {
}
