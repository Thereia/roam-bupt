package com.roambupt.service.post.service;

import com.roambupt.api.model.dto.MediaRef;

import java.util.List;
import java.util.Set;

/**
 * 校验通过后的发帖数据：字段已规整，可以直接落库。
 */
public record ValidatedPost(String title,
                            String content,
                            String status,
                            List<Long> topicIds,
                            Set<Long> placeIds,
                            List<MediaRef> media) {
}
