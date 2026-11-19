package com.roambupt.service.post.service.impl;

import com.roambupt.service.post.repository.entity.PostDO;
import com.roambupt.service.post.repository.mapper.PostMapper;
import com.roambupt.service.post.service.PostQueryService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 帖子读能力实现。 */
@Service
public class PostQueryServiceImpl implements PostQueryService {

    private final PostMapper postMapper;

    public PostQueryServiceImpl(PostMapper postMapper) {
        this.postMapper = postMapper;
    }

    @Override
    public boolean existsOnline(long postId) {
        PostDO post = postMapper.selectById(postId);
        return post != null && "ONLINE".equals(post.getStatus());
    }

    @Override
    public Long findAuthorId(long postId) {
        PostDO post = postMapper.selectById(postId);
        return post == null ? null : post.getUserId();
    }

    @Override
    public List<Long> searchIds(String keyword, Long topicId, int offset, int size) {
        return postMapper.searchIds(keyword, topicId, offset, size);
    }

    @Override
    public long countSearch(String keyword, Long topicId) {
        return postMapper.countSearch(keyword, topicId);
    }
}
