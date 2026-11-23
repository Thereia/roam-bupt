package com.roambupt.api.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 评价入参：星必填，文字与图片可选。 */
public record RatingCommand(
        @Min(1) @Max(5) int score,
        @Size(max = 200, message = "评价最多 200 字") String review,
        @Size(max = 3, message = "最多上传 3 张图")
        List<@Pattern(regexp = "^/files/.*", message = "图片必须是站内上传的文件") String> images) {
}
