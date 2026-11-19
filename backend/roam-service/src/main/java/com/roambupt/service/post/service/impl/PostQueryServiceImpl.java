package com.roambupt.service.post.service.impl;

import com.roambupt.service.post.repository.entity.PostDO;
import com.roambupt.service.post.repository.mapper.PostMapper;
import com.roambupt.service.post.service.PostQueryService;
import org.springframework.stereotype.Service;

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
}
