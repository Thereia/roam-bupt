package com.roambupt.service.interaction.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.event.PostLikedEvent;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.InteractionResultVO;
import com.roambupt.service.interaction.repository.entity.UserFootDO;
import com.roambupt.service.interaction.repository.mapper.UserFootMapper;
import com.roambupt.service.interaction.service.InteractionService;
import com.roambupt.service.post.service.PostQueryService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 点赞/收藏实现。
 *
 * <p>幂等靠唯一键：冲突直接当成功；只有真正插入成功（affectedRows = 1）才发事件。</p>
 */
@Service
public class InteractionServiceImpl implements InteractionService {

    private static final String LIKE = "LIKE";
    private static final String COLLECT = "COLLECT";

    private final UserFootMapper userFootMapper;
    private final PostQueryService postQueryService;
    private final ApplicationEventPublisher eventPublisher;

    public InteractionServiceImpl(UserFootMapper userFootMapper,
                                  PostQueryService postQueryService,
                                  ApplicationEventPublisher eventPublisher) {
        this.userFootMapper = userFootMapper;
        this.postQueryService = postQueryService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InteractionResultVO like(long postId, long uid) {
        return add(postId, uid, LIKE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InteractionResultVO unlike(long postId, long uid) {
        return remove(postId, uid, LIKE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InteractionResultVO collect(long postId, long uid) {
        return add(postId, uid, COLLECT);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InteractionResultVO uncollect(long postId, long uid) {
        return remove(postId, uid, COLLECT);
    }

    @Override
    public java.util.List<Long> collectedPostIds(long uid, int page, int pageSize) {
        int pageNo = Math.max(1, page);
        int size = Math.min(Math.max(1, pageSize), 50);
        return userFootMapper.findCollectedPostIds(uid, (pageNo - 1) * size, size);
    }

    @Override
    public long countCollected(long uid) {
        return userFootMapper.countCollected(uid);
    }

    private InteractionResultVO add(long postId, long uid, String action) {
        requireOnline(postId);
        boolean inserted;
        try {
            UserFootDO foot = new UserFootDO();
            foot.setUserId(uid);
            foot.setPostId(postId);
            foot.setAction(action);
            foot.setCreatedTime(LocalDateTime.now());
            userFootMapper.insert(foot);
            inserted = true;
        } catch (DuplicateKeyException e) {
            // 已经点过，按幂等处理
            inserted = false;
        }
        if (inserted) {
            eventPublisher.publishEvent(new PostLikedEvent(postId, uid, action));
        }
        return new InteractionResultVO(true, userFootMapper.countByPost(action, postId));
    }

    private InteractionResultVO remove(long postId, long uid, String action) {
        requireOnline(postId);
        userFootMapper.delete(Wrappers.<UserFootDO>lambdaQuery()
                .eq(UserFootDO::getUserId, uid)
                .eq(UserFootDO::getPostId, postId)
                .eq(UserFootDO::getAction, action));
        return new InteractionResultVO(false, userFootMapper.countByPost(action, postId));
    }

    private void requireOnline(long postId) {
        if (!postQueryService.existsOnline(postId)) {
            throw new BusinessException(StatusEnum.NOT_FOUND, "帖子不存在");
        }
    }
}
