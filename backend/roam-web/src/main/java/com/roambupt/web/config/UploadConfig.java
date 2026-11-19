package com.roambupt.web.config;

import com.roambupt.service.storage.UploadProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/** 上传目录的静态访问：/files/** 直接读磁盘，不走鉴权。 */
@Configuration
@EnableConfigurationProperties(UploadProperties.class)
public class UploadConfig implements WebMvcConfigurer {

    private final UploadProperties properties;

    public UploadConfig(UploadProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Paths.get(properties.dir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler(properties.allowedPrefix() + "**")
                .addResourceLocations(location);
    }
}
