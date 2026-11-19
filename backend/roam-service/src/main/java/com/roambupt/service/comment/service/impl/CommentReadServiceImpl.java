package com.roambupt.service.comment.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.api.model.vo.CommentVO;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.UserBriefVO;
import com.roambupt.api.model.vo.UserVO;
import com.roambupt.service.comment.repository.entity.CommentDO;
import com.roambupt.service.comment.repository.mapper.CommentMapper;
import com.roambupt.service.comment.service.CommentReadService;
import com.roambupt.service.user.service.UserQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 评论读服务实现。 */
@Service
public class CommentReadServiceImpl implements CommentReadService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CommentMapper commentMapper;
    private final UserQueryService userQueryService;

    public CommentReadServiceImpl(CommentMapper commentMapper, UserQueryService userQueryService) {
        this.commentMapper = commentMapper;
        this.userQueryService = userQueryService;
    }

    @Override
    public PageResult<CommentVO> listTop(long postId, int page, int pageSize) {
        int pageNo = Math.max(1, page);
        int size = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        List<CommentDO> rows = commentMapper.selectList(Wrappers.<CommentDO>lambdaQuery()
                .eq(CommentDO::getPostId, postId)
                .eq(CommentDO::getTopId, 0)
                .eq(CommentDO::getDeleted, 0)
                .orderByDesc(CommentDO::getCreatedTime)
                .last("LIMIT " + ((pageNo - 1) * size) + ", " + size));
        long total = commentMapper.countTopByPost(postId);
        return PageResult.of(total, pageNo, size, toVOList(rows));
    }

    @Override
    public PageResult<CommentVO> listReplies(long topCommentId, int page, int pageSize) {
        int pageNo = Math.max(1, page);
        int size = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        List<CommentDO> rows = commentMapper.selectList(Wrappers.<CommentDO>lambdaQuery()
                .eq(CommentDO::getTopId, topCommentId)
                .eq(CommentDO::getDeleted, 0)
                .orderByAsc(CommentDO::getCreatedTime)
                .last("LIMIT " + ((pageNo - 1) * size) + ", " + size));
        long total = commentMapper.selectCount(Wrappers.<CommentDO>lambdaQuery()
                .eq(CommentDO::getTopId, topCommentId)
                .eq(CommentDO::getDeleted, 0));
        return PageResult.of(total, pageNo, size, toVOList(rows));
    }

    private List<CommentVO> toVOList(List<CommentDO> rows) {
        Map<Long, UserBriefVO> authors = rows.stream()
                .map(CommentDO::getUserId)
                .distinct()
                .map(uid -> userQueryService.getById(uid).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserVO::id,
                        user -> new UserBriefVO(user.id(), user.nickname(), user.avatar(), null)));
        return rows.stream()
                .map(row -> new CommentVO(row.getId(), row.getPostId(), row.getContent(),
                        authors.get(row.getUserId()), row.getParentId(), row.getTopId(),
                        row.getReplyCount() == null ? 0 : row.getReplyCount(),
                        row.getCreatedTime()))
                .toList();
    }
}
