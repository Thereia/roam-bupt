package com.roambupt.core.session;

/**
 * 会话校验结果。UNAVAILABLE 表示存储不可用，不能当成"未登录"。
 */
public enum SessionStatus {
    VALID, ABSENT, UNAVAILABLE
}
