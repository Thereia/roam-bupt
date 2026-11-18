package com.roambupt.core.jwt;

/**
 * 解析结果：只取身份，不取权限（权限每请求查库）。
 */
public record ParsedToken(long uid, String jti) {
}
