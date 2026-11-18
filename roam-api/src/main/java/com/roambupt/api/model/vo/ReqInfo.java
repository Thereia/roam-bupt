package com.roambupt.api.model.vo;

/**
 * 当前请求的身份快照，存进 ThreadLocal。
 */
public record ReqInfo(Long uid, String role, String jti) {
}
