package com.roambupt.service.post.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.service.post.repository.entity.PostDO;
import com.roambupt.service.post.repository.entity.PostTopicDO;
import com.roambupt.service.post.repository.mapper.PostMapper;
import com.roambupt.service.post.repository.mapper.PostTopicMapper;
import com.roambupt.service.topic.repository.entity.TopicDO;
import com.roambupt.service.topic.repository.mapper.TopicMapper;
import com.roambupt.service.post.service.PostQueryService;
import org.springframework.stereotype.Service;

import java.util.List;

/** 帖子读能力实现。 */
@Service
public class PostQueryServiceImpl implements PostQueryService {

    private final PostMapper postMapper;
    private final PostTopicMapper postTopicMapper;
    private final TopicMapper topicMapper;

    public PostQueryServiceImpl(PostMapper postMapper, PostTopicMapper postTopicMapper,
                                TopicMapper topicMapper) {
        this.postMapper = postMapper;
        this.postTopicMapper = postTopicMapper;
        this.topicMapper = topicMapper;
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

    @Override
    public long countOnlineByUser(long uid) {
        return postMapper.countByUser(uid, false);
    }

    @Override
    public String findTitle(long postId) {
        PostDO post = postMapper.selectById(postId);
        return post == null ? null : post.getTitle();
    }

    @Override
    public java.util.Map<Long, String> titlesOf(java.util.Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<Long, String> titles = new java.util.HashMap<>();
        for (PostDO post : postMapper.selectBatchIds(postIds)) {
            titles.put(post.getId(), post.getTitle());
        }
        return titles;
    }

    @Override
    public String findStatus(long postId) {
        PostDO post = postMapper.selectById(postId);
        return post == null ? null : post.getStatus();
    }

    @Override
    public List<Long> findOrderedPlaceTopicIds(long postId) {
        List<PostTopicDO> links = postTopicMapper.selectList(Wrappers.<PostTopicDO>lambdaQuery()
                .eq(PostTopicDO::getPostId, postId)
                .orderByAsc(PostTopicDO::getSortNo));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> topicIds = links.stream().map(PostTopicDO::getTopicId).toList();
        return topicMapper.selectBatchIds(topicIds).stream()
                .filter(topic -> "PLACE".equals(topic.getType()))
                .filter(topic -> topic.getEnabled() != null && topic.getEnabled() == 1)
                .sorted(java.util.Comparator.comparing(topic -> topicIds.indexOf(topic.getId())))
                .map(TopicDO::getId)
                .toList();
    }
}
