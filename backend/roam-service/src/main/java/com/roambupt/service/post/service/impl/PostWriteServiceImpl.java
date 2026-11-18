package com.roambupt.service.post.service.impl;

import com.roambupt.api.model.dto.MediaRef;
import com.roambupt.api.model.dto.PostCreateCommand;
import com.roambupt.api.model.event.PostCreatedEvent;
import com.roambupt.api.model.vo.PostCreatedVO;
import com.roambupt.service.post.repository.entity.PostDO;
import com.roambupt.service.post.repository.entity.PostMediaDO;
import com.roambupt.service.post.repository.entity.PostTopicDO;
import com.roambupt.service.post.repository.mapper.PostMapper;
import com.roambupt.service.post.repository.mapper.PostMediaMapper;
import com.roambupt.service.post.repository.mapper.PostTopicMapper;
import com.roambupt.service.post.service.PostCreateValidator;
import com.roambupt.service.post.service.PostWriteService;
import com.roambupt.service.post.service.ValidatedPost;
import com.roambupt.service.topic.service.TopicCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * 帖子写服务实现：只做编排，规则在 {@link PostCreateValidator}。
 *
 * <p>三个 insert 在同一事务里；事件在提交后发布，回滚不会留下悬挂通知。</p>
 */
@Service
public class PostWriteServiceImpl implements PostWriteService {

    private static final Logger log = LoggerFactory.getLogger(PostWriteServiceImpl.class);
    private static final String MEDIA_IMAGE = "IMAGE";

    private final PostMapper postMapper;
    private final PostTopicMapper postTopicMapper;
    private final PostMediaMapper postMediaMapper;
    private final PostCreateValidator validator;
    private final TopicCommandService topicCommandService;
    private final ApplicationEventPublisher eventPublisher;

    public PostWriteServiceImpl(PostMapper postMapper,
                                PostTopicMapper postTopicMapper,
                                PostMediaMapper postMediaMapper,
                                PostCreateValidator validator,
                                TopicCommandService topicCommandService,
                                ApplicationEventPublisher eventPublisher) {
        this.postMapper = postMapper;
        this.postTopicMapper = postTopicMapper;
        this.postMediaMapper = postMediaMapper;
        this.validator = validator;
        this.topicCommandService = topicCommandService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostCreatedVO create(PostCreateCommand command, long uid) {
        ValidatedPost input = validator.validate(command, uid);
        PostDO post = insertPost(input, uid);
        bindTopics(post.getId(), input);
        bindMedia(post.getId(), input);
        topicCommandService.addHeat(input.placeIds());
        publishAfterCommit(post.getId(), uid);
        return new PostCreatedVO(post.getId(), input.status());
    }

    private PostDO insertPost(ValidatedPost input, long uid) {
        PostDO post = new PostDO();
        post.setUserId(uid);
        post.setTitle(input.title());
        post.setContent(input.content());
        post.setStatus(input.status());
        post.setHeat(0);
        post.setCreatedTime(LocalDateTime.now());
        post.setUpdatedTime(LocalDateTime.now());
        postMapper.insert(post);
        return post;
    }

    private void bindTopics(long postId, ValidatedPost input) {
        int placeOrder = 0;
        for (Long topicId : input.topicIds()) {
            PostTopicDO relation = new PostTopicDO();
            relation.setPostId(postId);
            relation.setTopicId(topicId);
            // 只有地点参与路线顺序，分类固定 0
            relation.setSortNo(input.placeIds().contains(topicId) ? placeOrder++ : 0);
            postTopicMapper.insert(relation);
        }
    }

    private void bindMedia(long postId, ValidatedPost input) {
        int order = 0;
        for (MediaRef item : input.media()) {
            PostMediaDO entity = new PostMediaDO();
            entity.setPostId(postId);
            entity.setMediaType(MEDIA_IMAGE);
            entity.setUrl(item.url());
            entity.setSortNo(order++);
            postMediaMapper.insert(entity);
        }
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
