package com.roambupt.api.model.vo;

/**
 * 注册/登录的统一返回：token + 用户信息。
 */
public record AuthResult(String token, UserVO user) {
}
