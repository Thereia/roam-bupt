package com.roambupt.api.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册入参。用户名只允许字母、数字、下划线，长度 3-32。
 */
public record RegisterCommand(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "用户名只允许字母、数字、下划线，长度 3-32")
        String username,

        @NotBlank
        @Size(min = 6, max = 32, message = "密码长度 6-32")
        String password,

        @NotBlank
        @Size(max = 32, message = "昵称最长 32 字")
        String nickname) {
}
