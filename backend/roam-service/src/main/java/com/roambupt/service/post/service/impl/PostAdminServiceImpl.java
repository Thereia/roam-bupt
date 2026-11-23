package com.roambupt.service.post.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.event.PostStatusChangedEvent;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.PostVO;
import com.roambupt.service.post.repository.entity.PostDO;
import com.roambupt.service.post.repository.mapper.PostMapper;
import com.roambupt.service.post.service.PostAdminService;
import com.roambupt.service.post.service.PostReadService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** 后台帖子：审核状态机。 */
@Service
public class PostAdminServiceImpl implements PostAdminService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "DRAFT", Set.of("PENDING", "ONLINE", "DELETED"),
            "PENDING", Set.of("ONLINE", "DELETED"),
            "ONLINE", Set.of("OFFLINE", "DELETED"),
            "OFFLINE", Set.of("ONLINE", "DELETED"),
            "DELETED", Set.of());

    private final PostMapper postMapper;
    private final PostReadService postReadService;
    private final ApplicationEventPublisher eventPublisher;

    public PostAdminServiceImpl(PostMapper postMapper,
                                PostReadService postReadService,
                                ApplicationEventPublisher eventPublisher) {
        this.postMapper = postMapper;
        this.postReadService = postReadService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public PageResult<PostVO> listByStatus(String status, int page, int pageSize) {
        int pageNo = Math.max(1, page);
        int size = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        List<PostDO> posts = postMapper.pageByStatus(status, (pageNo - 1) * size, size);
        long total = postMapper.countByStatus(status);
        return PageResult.of(total, pageNo, size,
                postReadService.assembleAll(posts, null));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(long postId, String targetStatus) {
        PostDO post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(StatusEnum.NOT_FOUND, "帖子不存在");
        }
        String current = post.getStatus();
        String target = targetStatus == null ? "" : targetStatus.toUpperCase();
        if (!ALLOWED.getOrDefault(current, Set.of()).contains(target)) {
            throw new BusinessException(StatusEnum.CONFLICT,
                    "状态不能从 " + current + " 变为 " + target);
        }
        postMapper.update(null, Wrappers.<PostDO>lambdaUpdate()
                .eq(PostDO::getId, postId)
                .set(PostDO::getStatus, target));
        eventPublisher.publishEvent(new PostStatusChangedEvent(postId, target));
    }
}
