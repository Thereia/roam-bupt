package com.roambupt.service.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 搜索配置。ES 关闭或不可用时走 MySQL 兜底。
 */
@ConfigurationProperties(prefix = "roam.search")
public record SearchProperties(boolean enabled, String host, int port, String index,
                               int connectTimeoutMs, int socketTimeoutMs) {
}
