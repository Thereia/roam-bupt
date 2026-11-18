package com.roambupt.core.limit;

/**
 * 登录/注册限流。按 客户端 IP + 用户名 计数。
 */
public interface LoginRateLimiter {

    boolean tryAcquire(String clientIp, String username);
}
