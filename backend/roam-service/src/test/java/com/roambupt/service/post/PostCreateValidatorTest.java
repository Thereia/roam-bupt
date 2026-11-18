package com.roambupt.service.post;

import com.roambupt.api.model.dto.MediaRef;
import com.roambupt.api.model.dto.PostCreateCommand;
import com.roambupt.api.model.dto.TopicRef;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.service.config.PostProperties;
import com.roambupt.service.post.service.PostCreateValidator;
import com.roambupt.service.post.service.ValidatedPost;
import com.roambupt.service.topic.service.TopicService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 发帖校验的单元测试：不起 Spring，只测规则本身。
 */
class PostCreateValidatorTest {

    private static final long PLACE_ID = 113L;
    private static final long CATEGORY_ID = 1002L;

    private TopicService topicService;
    private PostCreateValidator validator;

    @BeforeEach
    void setUp() {
        topicService = mock(TopicService.class);
        when(topicService.findEnabledPlaceIds(any())).thenReturn(Set.of(PLACE_ID));
        validator = new PostCreateValidator(topicService, new PostProperties(true));
    }

    private PostCreateCommand command(String title, String content, List<TopicRef> topics) {
        return new PostCreateCommand(title, content, null, topics, List.of());
    }

    @Test
    void 校验_只挂分类_抛4001() {
        when(topicService.findEnabledPlaceIds(any())).thenReturn(Set.of());
        PostCreateCommand cmd = command("标题", "正文", List.of(new TopicRef(CATEGORY_ID, 0)));

        assertThatThrownBy(() -> validator.validate(cmd, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(StatusEnum.PARAM_ERROR.getCode());
    }

    @Test
    void 校验_主题重复_抛4001() {
        PostCreateCommand cmd = command("标题", "正文",
                List.of(new TopicRef(PLACE_ID, 0), new TopicRef(PLACE_ID, 1)));

        assertThatThrownBy(() -> validator.validate(cmd, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同一个主题不能重复挂载");
    }

    @Test
    void 校验_主题超过9个_抛4001() {
        List<TopicRef> topics = Collections.nCopies(10, new TopicRef(PLACE_ID, 0));
        PostCreateCommand cmd = command("标题", "正文", topics);

        assertThatThrownBy(() -> validator.validate(cmd, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("主题数量需在 1-9 之间");
    }

    @Test
    void 校验_正文超过48KB_抛4001() {
        PostCreateCommand cmd = command("标题", "a".repeat(50 * 1024), List.of(new TopicRef(PLACE_ID, 0)));

        assertThatThrownBy(() -> validator.validate(cmd, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("正文超过 48KB");
    }

    @Test
    void 校验_媒体不是图片_抛4001() {
        PostCreateCommand cmd = new PostCreateCommand("标题", "正文", null,
                List.of(new TopicRef(PLACE_ID, 0)),
                List.of(new MediaRef("VIDEO", "https://example.com/a.mp4", 0)));

        assertThatThrownBy(() -> validator.validate(cmd, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("P0 只支持图片");
    }

    @Test
    void 校验_正常_自动通过时为ONLINE() {
        PostCreateCommand cmd = command("标题", "正文", List.of(new TopicRef(PLACE_ID, 0)));

        ValidatedPost result = validator.validate(cmd, 1L);

        assertThat(result.status()).isEqualTo("ONLINE");
        assertThat(result.topicIds()).containsExactly(PLACE_ID);
        assertThat(result.placeIds()).containsExactly(PLACE_ID);
    }
}
