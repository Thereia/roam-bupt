package com.roambupt.api.model.dto;

/** 帖子媒体。P0 只收 IMAGE。 */
public record MediaRef(String type, String url, Integer sortNo) {
}
