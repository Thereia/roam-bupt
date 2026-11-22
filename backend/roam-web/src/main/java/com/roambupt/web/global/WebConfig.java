package com.roambupt.web.global;

import com.roambupt.core.config.AuthProperties;
import com.roambupt.core.config.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层装配：注册鉴权拦截器与白名单。
 */
@Configuration
@EnableConfigurationProperties({AuthProperties.class, SecurityProperties.class})
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final WriteRateLimitInterceptor writeRateLimitInterceptor;
    private final AuthProperties authProperties;

    public WebConfig(AuthInterceptor authInterceptor,
                     WriteRateLimitInterceptor writeRateLimitInterceptor,
                     AuthProperties authProperties) {
        this.authInterceptor = authInterceptor;
        this.writeRateLimitInterceptor = writeRateLimitInterceptor;
        this.authProperties = authProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(authProperties.excludePaths());
        // 放在鉴权之后：这样才能按 uid 计数
        registry.addInterceptor(writeRateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(authProperties.excludePaths());
    }
}
