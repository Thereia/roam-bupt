package com.roambupt.service.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 帖子域配置绑定。 */
@Configuration
@EnableConfigurationProperties(PostProperties.class)
public class PostConfig {
}
