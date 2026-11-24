package com.roambupt.service.post.service;

import com.roambupt.api.model.dto.MediaRef;
import com.roambupt.api.model.dto.PostCreateCommand;
import com.roambupt.api.model.dto.TopicRef;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.service.config.PostProperties;
import com.roambupt.core.sensitive.SensitiveWordFilter;
import com.roambupt.service.storage.UploadProperties;
import com.roambupt.service.topic.service.TopicService;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 发帖校验：只判断和规整，不写库。
 *
 * <p>分成独立类是为了让规则可单独测试，也让 {@code PostWriteService} 只剩编排。</p>
 */
@Component
public class PostCreateValidator {

    private static final int MAX_TOPICS = 9;
    private static final int MAX_MEDIA = 9;
    private static final int MAX_CONTENT_CHARS = 20000;
    private static final String MEDIA_IMAGE = "IMAGE";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_ONLINE = "ONLINE";
    private static final String STATUS_PENDING = "PENDING";

    private final TopicService topicService;
    private final PostProperties postProperties;
    private final UploadProperties uploadProperties;
    private final SensitiveWordFilter sensitiveWordFilter;

    public PostCreateValidator(TopicService topicService, PostProperties postProperties,
                               UploadProperties uploadProperties,
                               SensitiveWordFilter sensitiveWordFilter) {
        this.topicService = topicService;
        this.postProperties = postProperties;
        this.uploadProperties = uploadProperties;
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    public ValidatedPost validate(PostCreateCommand command, long uid) {
        if (uid <= 0) {
            throw new BusinessException(StatusEnum.UNAUTHORIZED);
        }

        String content = command.content();
        require(!sensitiveWordFilter.hit(command.title()) && !sensitiveWordFilter.hit(content),
                "内容包含敏感词");
        require(content.length() <= MAX_CONTENT_CHARS, "正文最多 20000 字");

        List<TopicRef> refs = command.topics() == null ? List.of() : command.topics();
        // 地点和分类都可以，只要求至少挂一个主题；有没有地点只影响路线能力
        require(!refs.isEmpty() && refs.size() <= MAX_TOPICS, "至少挂一个主题（地点或分类）");

        List<Long> topicIds = refs.stream().map(TopicRef::topicId).toList();
        require(topicIds.stream().noneMatch(Objects::isNull), "主题 id 不能为空");
        require(new HashSet<>(topicIds).size() == topicIds.size(), "同一个主题不能重复挂载");

        // 主题必须是存在且启用的（地点或分类），否则会挂出“没有主题”的帖子
        require(topicService.findEnabledIds(topicIds).size() == topicIds.size(), "存在不可用的主题");

        Set<Long> placeIds = topicService.findEnabledPlaceIds(topicIds);

        List<MediaRef> media = command.media() == null ? List.of() : command.media();
        require(media.size() <= MAX_MEDIA, "媒体最多 9 个");
        require(media.stream().allMatch(item -> MEDIA_IMAGE.equalsIgnoreCase(item.type())), "只支持图片");
        require(media.stream().allMatch(item -> item.url() != null
                        && item.url().startsWith(uploadProperties.allowedPrefix())), "图片地址不合法");

        return new ValidatedPost(command.title().trim(), content, resolveStatus(command.status()),
                topicIds, placeIds, media);
    }

    /** 发布草稿时用的状态：与新建一致，由 auto-approve 决定。 */
    public String statusForPublish() {
        return postProperties.autoApprove() ? STATUS_ONLINE : STATUS_PENDING;
    }

    private String resolveStatus(String requested) {
        if (STATUS_DRAFT.equalsIgnoreCase(requested)) {
            return STATUS_DRAFT;
        }
        return postProperties.autoApprove() ? STATUS_ONLINE : STATUS_PENDING;
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, message);
        }
    }
}
