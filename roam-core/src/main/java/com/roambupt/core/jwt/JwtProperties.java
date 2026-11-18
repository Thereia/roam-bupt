package com.roambupt.core.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置：密钥与有效期。
 */
@ConfigurationProperties(prefix = "roam.jwt")
public record JwtProperties(String secret, Duration ttl) {
}
