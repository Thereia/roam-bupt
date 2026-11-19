package com.roambupt.service.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 上传配置。
 *
 * @param dir             本地存储根目录
 * @param allowedPrefix  发帖时媒体 URL 必须以此开头
 * @param maxSizeMb      单文件上限（MB）
 */
@ConfigurationProperties(prefix = "roam.upload")
public record UploadProperties(String dir, String allowedPrefix, int maxSizeMb) {
}
