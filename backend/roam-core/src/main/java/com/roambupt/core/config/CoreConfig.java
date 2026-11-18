package com.roambupt.core.config;

import com.roambupt.core.jwt.JwtProperties;
import com.roambupt.core.jwt.JwtSupport;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * core 层的装配：JWT 与认证配置。
 */
@Configuration
@EnableConfigurationProperties({JwtProperties.class, AuthProperties.class})
public class CoreConfig {

    @Bean
    public JwtSupport jwtSupport(JwtProperties properties) {
        return new JwtSupport(properties);
    }
}
