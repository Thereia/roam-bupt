package com.roambupt.service.topic.service;

/**
 * 主题域需要的帖子计数。由 post 域实现，避免 topic → post 的直接依赖成环。
 */
public interface PostCountProvider {

    long countOnlineByTopic(long topicId);
}
