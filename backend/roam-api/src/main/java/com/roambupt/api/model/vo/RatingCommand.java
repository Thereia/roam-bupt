package com.roambupt.api.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 打分入参。 */
public record RatingCommand(@Min(1) @Max(5) int score) {
}
