package com.roambupt.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 认证相关配置。
 *
 * @param redisFailOpen      Redis 不可用时是否降级放行（默认 false，认证是安全边界）
 * @param loginLimitPerMinute 单 IP + 用户名的每分钟登录尝试上限
 * @param excludePaths       不需要登录的路径
 */
@ConfigurationProperties(prefix = "roam.auth")
public record AuthProperties(boolean redisFailOpen, int loginLimitPerMinute, List<String> excludePaths) {
}
