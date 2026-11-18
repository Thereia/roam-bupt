package com.roambupt.api.model.vo;

import java.time.LocalDateTime;
import java.util.List;

/** 帖子视图。列表不返回 content，详情才返回。 */
public record PostVO(Long id, String title, String excerpt, String content, String status,
                     UserBriefVO author, List<TopicBriefVO> topics, List<MediaVO> media,
                     CountsVO counts, Boolean myLiked, Boolean myCollected,
                     LocalDateTime createdTime, LocalDateTime updatedTime) {
}
