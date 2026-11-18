package com.roambupt.service.post.service.impl;

import com.roambupt.api.model.dto.MediaRef;
import com.roambupt.api.model.dto.PostCreateCommand;
import com.roambupt.api.model.dto.TopicRef;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.event.PostCreatedEvent;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.PostCreatedVO;
import com.roambupt.service.config.PostProperties;
import com.roambupt.service.post.repository.entity.PostDO;
import com.roambupt.service.post.repository.entity.PostMediaDO;
import com.roambupt.service.post.repository.entity.PostTopicDO;
import com.roambupt.service.post.repository.mapper.PostMapper;
import com.roambupt.service.post.repository.mapper.PostMediaMapper;
import com.roambupt.service.post.repository.mapper.PostTopicMapper;
import com.roambupt.service.post.service.PostWriteService;
import com.roambupt.service.topic.service.TopicService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 帖子写服务实现。
 *
 * <p>三个 insert 在同一事务里；事件在提交后发布，避免回滚后通知悬挂。</p>
 */
@Service
public class PostWriteServiceImpl implements PostWriteService {

    private static final Logger log = LoggerFactory.getLogger(PostWriteServiceImpl.class);

    private static final int MAX_TOPICS = 9;
    private static final int MAX_MEDIA = 9;
    private static final int MAX_CONTENT_BYTES = 48 * 1024;

    private final PostMapper postMapper;
    private final PostTopicMapper postTopicMapper;
    private final PostMediaMapper postMediaMapper;
    private final TopicService topicService;
    private final PostProperties postProperties;
    private final ApplicationEventPublisher eventPublisher;

    public PostWriteServiceImpl(PostMapper postMapper,
                                PostTopicMapper postTopicMapper,
                                PostMediaMapper postMediaMapper,
                                TopicService topicService,
                                PostProperties postProperties,
                                ApplicationEventPublisher eventPublisher) {
        this.postMapper = postMapper;
        this.postTopicMapper = postTopicMapper;
        this.postMediaMapper = postMediaMapper;
        this.topicService = topicService;
        this.postProperties = postProperties;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostCreatedVO create(PostCreateCommand command, long uid) {
        if (uid <= 0) {
            throw new BusinessException(StatusEnum.UNAUTHORIZED);
        }
        String content = command.content();
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_CONTENT_BYTES) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "正文超过 48KB");
        }

        List<TopicRef> refs = command.topics() == null ? List.of() : command.topics();
        if (refs.isEmpty() || refs.size() > MAX_TOPICS) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "主题数量需在 1-9 之间");
        }
        List<Long> topicIds = refs.stream().map(TopicRef::topicId).toList();
        if (topicIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "主题 id 不能为空");
        }
        if (new HashSet<>(topicIds).size() != topicIds.size()) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "同一个主题不能重复挂载");
        }

        Set<Long> placeIds = topicService.findEnabledPlaceIds(topicIds);
        if (placeIds.isEmpty()) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "至少挂一个地点");
        }

        List<MediaRef> media = command.media() == null ? List.of() : command.media();
        if (media.size() > MAX_MEDIA) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "媒体最多 9 个");
        }
        for (MediaRef item : media) {
            if (!"IMAGE".equalsIgnoreCase(item.type())) {
                throw new BusinessException(StatusEnum.PARAM_ERROR, "P0 只支持图片");
            }
        }

        String status = resolveStatus(command.status());

        PostDO post = new PostDO();
        post.setUserId(uid);
        post.setTitle(command.title().trim());
        post.setContent(content);
        post.setStatus(status);
        post.setHeat(0);
        post.setCreatedTime(LocalDateTime.now());
        post.setUpdatedTime(LocalDateTime.now());
        postMapper.insert(post);

        int placeOrder = 0;
        for (Long topicId : topicIds) {
            PostTopicDO relation = new PostTopicDO();
            relation.setPostId(post.getId());
            relation.setTopicId(topicId);
            // 只有地点参与路线顺序，分类固定 0
            relation.setSortNo(placeIds.contains(topicId) ? placeOrder++ : 0);
            postTopicMapper.insert(relation);
        }

        int mediaOrder = 0;
        for (MediaRef item : media) {
            PostMediaDO entity = new PostMediaDO();
            entity.setPostId(post.getId());
            entity.setMediaType("IMAGE");
            entity.setUrl(item.url());
            entity.setSortNo(item.sortNo() == null ? mediaOrder : item.sortNo());
            postMediaMapper.insert(entity);
            mediaOrder++;
        }

        publishAfterCommit(post.getId(), uid);
        return new PostCreatedVO(post.getId(), status);
    }

    private String resolveStatus(String requested) {
        if ("DRAFT".equalsIgnoreCase(requested)) {
            return "DRAFT";
        }
        return postProperties.autoApprove() ? "ONLINE" : "PENDING";
    }

    private void publishAfterCommit(long postId, long uid) {
        PostCreatedEvent event = new PostCreatedEvent(postId, uid);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(event);
                }
            });
        } else {
            log.warn("post.created.event.published_without_tx postId={}", postId);
            eventPublisher.publishEvent(event);
        }
    }
}
