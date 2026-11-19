package com.roambupt.service.comment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.api.model.dto.CommentCreateCommand;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.event.CommentCreatedEvent;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.CommentCreatedVO;
import com.roambupt.service.comment.repository.entity.CommentDO;
import com.roambupt.service.comment.repository.mapper.CommentMapper;
import com.roambupt.service.comment.service.CommentWriteService;
import com.roambupt.core.sensitive.SensitiveWordFilter;
import com.roambupt.service.post.service.PostQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 评论写服务实现。 */
@Service
public class CommentWriteServiceImpl implements CommentWriteService {

    private static final Logger log = LoggerFactory.getLogger(CommentWriteServiceImpl.class);
    private static final String ROLE_ADMIN = "ADMIN";
    private static final int DELETED = 1;

    private final CommentMapper commentMapper;
    private final PostQueryService postQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final SensitiveWordFilter sensitiveWordFilter;

    public CommentWriteServiceImpl(CommentMapper commentMapper,
                                   PostQueryService postQueryService,
                                   ApplicationEventPublisher eventPublisher,
                                   SensitiveWordFilter sensitiveWordFilter) {
        this.commentMapper = commentMapper;
        this.postQueryService = postQueryService;
        this.eventPublisher = eventPublisher;
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentCreatedVO create(long postId, CommentCreateCommand command, long uid) {
        if (!postQueryService.existsOnline(postId)) {
            throw new BusinessException(StatusEnum.NOT_FOUND, "帖子不存在");
        }
        String content = command.content().trim();
        if (sensitiveWordFilter.hit(content)) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "内容包含敏感词");
        }
        long parentId = command.parentId() == null ? 0L : command.parentId();
        long topId = 0L;

        if (parentId > 0) {
            CommentDO parent = commentMapper.selectById(parentId);
            if (parent == null || parent.getDeleted() != null && parent.getDeleted() == DELETED) {
                throw new BusinessException(StatusEnum.PARAM_ERROR, "回复的评论不存在");
            }
            if (!parent.getPostId().equals(postId)) {
                throw new BusinessException(StatusEnum.PARAM_ERROR, "回复的评论不属于该帖子");
            }
            topId = parent.getTopId() != null && parent.getTopId() > 0 ? parent.getTopId() : parent.getId();
            commentMapper.update(null, Wrappers.<CommentDO>lambdaUpdate()
                    .eq(CommentDO::getId, topId)
                    .setSql("reply_count = reply_count + 1"));
        }

        CommentDO comment = new CommentDO();
        comment.setPostId(postId);
        comment.setUserId(uid);
        comment.setContent(content);
        comment.setParentId(parentId);
        comment.setTopId(topId);
        comment.setReplyCount(0);
        comment.setDeleted(0);
        comment.setCreatedTime(LocalDateTime.now());
        commentMapper.insert(comment);

        // 与点赞事件一致：同步发布，消费方若需要 AFTER_COMMIT 自己加注解
        eventPublisher.publishEvent(new CommentCreatedEvent(postId, comment.getId(), uid,
                parentId == 0 ? null : parentId));
        return new CommentCreatedVO(comment.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(long commentId, long uid, String role) {
        CommentDO comment = commentMapper.selectById(commentId);
        if (comment == null || (comment.getDeleted() != null && comment.getDeleted() == DELETED)) {
            throw new BusinessException(StatusEnum.NOT_FOUND, "评论不存在");
        }
        if (!comment.getUserId().equals(uid) && !ROLE_ADMIN.equals(role)) {
            throw new BusinessException(StatusEnum.FORBIDDEN, "只能删除自己的评论");
        }

        commentMapper.update(null, Wrappers.<CommentDO>lambdaUpdate()
                .eq(CommentDO::getId, commentId)
                .set(CommentDO::getDeleted, DELETED));

        // 一级评论删除时连带隐藏它的回复，避免列表里出现孤儿
        if (comment.getTopId() != null && comment.getTopId() == 0) {
            commentMapper.update(null, Wrappers.<CommentDO>lambdaUpdate()
                    .eq(CommentDO::getTopId, commentId)
                    .set(CommentDO::getDeleted, DELETED));
        }
    }
}
