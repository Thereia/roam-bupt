package com.roambupt.service.comment.service.impl;

import com.roambupt.service.comment.repository.entity.CommentDO;
import com.roambupt.service.comment.repository.mapper.CommentMapper;
import com.roambupt.service.comment.service.CommentQueryService;
import org.springframework.stereotype.Service;

/** 评论读能力实现。 */
@Service
public class CommentQueryServiceImpl implements CommentQueryService {

    private final CommentMapper commentMapper;

    public CommentQueryServiceImpl(CommentMapper commentMapper) {
        this.commentMapper = commentMapper;
    }

    @Override
    public Long findAuthorId(long commentId) {
        CommentDO comment = commentMapper.selectById(commentId);
        return comment == null || (comment.getDeleted() != null && comment.getDeleted() == 1)
                ? null : comment.getUserId();
    }
}
