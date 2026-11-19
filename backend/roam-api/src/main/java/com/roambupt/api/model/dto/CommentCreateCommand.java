package com.roambupt.api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 发表评论入参。parentId 为空表示一级评论。 */
public record CommentCreateCommand(
        @NotBlank @Size(max = 1000) String content,
        Long parentId) {
}
