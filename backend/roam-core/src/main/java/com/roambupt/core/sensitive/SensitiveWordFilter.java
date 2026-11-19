package com.roambupt.core.sensitive;

/**
 * 敏感词判定。接口在 core（无业务依赖），实现在 service。
 */
public interface SensitiveWordFilter {

    boolean hit(String text);
}
