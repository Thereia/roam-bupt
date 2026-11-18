package com.roambupt.service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 帖子配置。
 *
 * @param autoApprove 为 true 时发帖直接 ONLINE，否则进 PENDING 等审核
 */
@ConfigurationProperties(prefix = "roam.post")
public record PostProperties(boolean autoApprove) {
}
