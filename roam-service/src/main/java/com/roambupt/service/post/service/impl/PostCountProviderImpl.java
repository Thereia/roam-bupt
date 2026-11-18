package com.roambupt.service.post.service.impl;

import com.roambupt.service.post.repository.mapper.PostMapper;
import com.roambupt.service.topic.service.PostCountProvider;
import org.springframework.stereotype.Service;

/** 向主题域提供帖子计数，避免 topic → post 的循环依赖。 */
@Service
public class PostCountProviderImpl implements PostCountProvider {

    private final PostMapper postMapper;

    public PostCountProviderImpl(PostMapper postMapper) {
        this.postMapper = postMapper;
    }

    @Override
    public long countOnlineByTopic(long topicId) {
        return postMapper.countByTopic(topicId);
    }
}
