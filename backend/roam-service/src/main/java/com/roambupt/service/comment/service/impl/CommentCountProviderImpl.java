package com.roambupt.service.comment.service.impl;

import com.roambupt.service.comment.repository.mapper.CommentMapper;
import com.roambupt.service.comment.service.CommentCountProvider;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** 评论计数实现。 */
@Service
public class CommentCountProviderImpl implements CommentCountProvider {

    private final CommentMapper commentMapper;

    public CommentCountProviderImpl(CommentMapper commentMapper) {
        this.commentMapper = commentMapper;
    }

    @Override
    public long countByPost(long postId) {
        return commentMapper.countByPost(postId);
    }

    @Override
    public Map<Long, Long> countByPosts(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        return commentMapper.countByPosts(postIds).stream()
                .collect(Collectors.toMap(CommentMapper.PostCountRow::getPostId,
                        CommentMapper.PostCountRow::getC));
    }
}
