package com.roambupt.service.notify.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.api.model.vo.NotificationVO;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.UserBriefVO;
import com.roambupt.api.model.vo.UserVO;
import com.roambupt.service.notify.repository.entity.NotificationDO;
import com.roambupt.service.notify.repository.mapper.NotificationMapper;
import com.roambupt.service.notify.service.NotificationService;
import com.roambupt.service.post.service.PostQueryService;
import com.roambupt.service.user.service.UserQueryService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 站内通知实现。 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);
    private static final int MAX_PAGE_SIZE = 50;

    private final NotificationMapper notificationMapper;
    private final UserQueryService userQueryService;
    private final PostQueryService postQueryService;

    public NotificationServiceImpl(NotificationMapper notificationMapper,
                                   UserQueryService userQueryService,
                                   PostQueryService postQueryService) {
        this.notificationMapper = notificationMapper;
        this.userQueryService = userQueryService;
        this.postQueryService = postQueryService;
    }

    @Override
    public void send(long recipientId, long actorId, String type,
                     String sourceType, long sourceId, Long postId, String content) {
        if (recipientId == actorId) {
            return;
        }
        NotificationDO notification = new NotificationDO();
        notification.setUserId(recipientId);
        notification.setActorId(actorId);
        notification.setType(type);
        notification.setSourceType(sourceType);
        notification.setSourceId(sourceId);
        notification.setPostId(postId);
        notification.setContent(content);
        notification.setReadFlag(0);
        notification.setCreatedTime(LocalDateTime.now());
        try {
            notificationMapper.insert(notification);
        } catch (DuplicateKeyException e) {
            // 同一个事件重复投递，唯一键兜底
            log.info("notification.duplicate recipient={} type={} source={}", recipientId, type, sourceId);
        }
    }

    @Override
    public PageResult<NotificationVO> list(long uid, String type, int page, int pageSize) {
        int pageNo = Math.max(1, page);
        int size = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        List<NotificationDO> rows = notificationMapper.selectList(Wrappers.<NotificationDO>lambdaQuery()
                .eq(NotificationDO::getUserId, uid)
                .eq(StringUtils.isNotBlank(type), NotificationDO::getType, type)
                .orderByDesc(NotificationDO::getCreatedTime)
                .last("LIMIT " + ((pageNo - 1) * size) + ", " + size));
        long total = notificationMapper.selectCount(Wrappers.<NotificationDO>lambdaQuery()
                .eq(NotificationDO::getUserId, uid)
                .eq(StringUtils.isNotBlank(type), NotificationDO::getType, type));

        Map<Long, UserBriefVO> actors = rows.stream()
                .map(NotificationDO::getActorId)
                .distinct()
                .map(actorId -> userQueryService.getById(actorId).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(UserVO::id,
                        user -> new UserBriefVO(user.id(), user.nickname(), user.avatar(), null)));

        var postIds = rows.stream().map(row -> row.getPostId()).filter(Objects::nonNull)
                .distinct().toList();
        var titles = postQueryService.titlesOf(postIds);

        List<NotificationVO> list = rows.stream()
                .map(row -> new NotificationVO(row.getId(), row.getType(),
                        actors.get(row.getActorId()), row.getSourceType(), row.getSourceId(),
                        row.getPostId(), row.getPostId() == null ? null : titles.get(row.getPostId()),
                        row.getContent(),
                        row.getReadFlag() != null && row.getReadFlag() == 1,
                        row.getCreatedTime()))
                .toList();
        return PageResult.of(total, pageNo, size, list);
    }

    @Override
    public long countUnread(long uid) {
        return notificationMapper.countUnread(uid);
    }

    @Override
    public void markRead(long uid, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        notificationMapper.update(null, Wrappers.<NotificationDO>lambdaUpdate()
                .eq(NotificationDO::getUserId, uid)
                .in(NotificationDO::getId, ids)
                .set(NotificationDO::getReadFlag, 1));
    }

    @Override
    public void markAllRead(long uid) {
        notificationMapper.update(null, Wrappers.<NotificationDO>lambdaUpdate()
                .eq(NotificationDO::getUserId, uid)
                .eq(NotificationDO::getReadFlag, 0)
                .set(NotificationDO::getReadFlag, 1));
    }
}
