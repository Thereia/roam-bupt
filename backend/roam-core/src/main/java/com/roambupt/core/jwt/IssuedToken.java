package com.roambupt.core.jwt;

import java.time.Instant;

/**
 * 签发结果：token 给客户端，jti 与服务端会话对应。
 */
public record IssuedToken(String token, String jti, Instant expiresAt) {
}
