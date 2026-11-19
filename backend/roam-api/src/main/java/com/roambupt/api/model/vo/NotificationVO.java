package com.roambupt.api.model.vo;

import java.time.LocalDateTime;

/** 站内通知视图。 */
public record NotificationVO(Long id, String type, UserBriefVO actor,
                             String sourceType, Long sourceId, Long postId,
                             String content, boolean read, LocalDateTime createdTime) {
}
