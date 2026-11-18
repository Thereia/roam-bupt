package com.roambupt.api.model.dto;

/**
 * 账号凭据视图：只在 auth 与 user 两个域之间传递，不出 Controller。
 */
public record UserAccount(Long id, String username, String password, String role, Integer status) {
}
