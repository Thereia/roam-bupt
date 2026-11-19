package com.roambupt.api.model.vo;

import java.time.LocalDateTime;

/** 评论视图。 */
public record CommentVO(Long id, Long postId, String content, UserBriefVO author,
                        Long parentId, Long topId, int replyCount, LocalDateTime createdTime) {
}
