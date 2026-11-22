package com.roambupt.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 代理与限流相关配置。
 *
 * @param trustForwardedFor 是否信任 X-Forwarded-For（只有部署在可信代理后才是 true）
 * @param trustedProxies    可信代理的地址列表；为空表示不限制来源
 * @param writePerMinute    单个用户/IP 每分钟写操作上限
 */
@ConfigurationProperties(prefix = "roam.security")
public record SecurityProperties(boolean trustForwardedFor, List<String> trustedProxies,
                                 int writePerMinute) {
}
