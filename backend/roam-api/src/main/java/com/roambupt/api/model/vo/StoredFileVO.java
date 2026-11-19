package com.roambupt.api.model.vo;

/** 上传结果：站内相对 URL 与图片尺寸（读不到尺寸时为 null）。 */
public record StoredFileVO(String url, Integer width, Integer height) {
}
