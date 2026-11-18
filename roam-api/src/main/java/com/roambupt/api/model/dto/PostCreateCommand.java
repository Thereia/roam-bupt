package com.roambupt.api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 发帖入参。 */
public record PostCreateCommand(
        @NotBlank @Size(max = 100) String title,
        @NotBlank @Size(max = 20000) String content,
        String status,
        @NotEmpty List<TopicRef> topics,
        List<MediaRef> media) {
}
