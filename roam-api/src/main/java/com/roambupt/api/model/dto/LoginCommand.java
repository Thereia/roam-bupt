package com.roambupt.api.model.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录入参。
 */
public record LoginCommand(
        @NotBlank String username,
        @NotBlank String password) {
}
