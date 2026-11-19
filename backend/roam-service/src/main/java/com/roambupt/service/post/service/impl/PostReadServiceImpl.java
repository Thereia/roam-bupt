package com.roambupt.service.post.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.api.model.dto.FeedQuery;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.CountsVO;
import com.roambupt.api.model.vo.MediaVO;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.PostVO;
import com.roambupt.api.model.vo.TopicBriefVO;
import com.roambupt.api.model.vo.UserBriefVO;
import com.roambupt.service.post.converter.PostConverter;
import com.roambupt.service.post.repository.entity.PostDO;
import com.roambupt.service.post.repository.entity.PostMediaDO;
import com.roambupt.service.post.repository.entity.PostTopicDO;
import com.roambupt.service.post.repository.mapper.PostMapper;
import com.roambupt.service.post.repository.mapper.PostMediaMapper;
import com.roambupt.service.post.repository.mapper.PostTopicMapper;
import com.roambupt.service.comment.service.CommentCountProvider;
import com.roambupt.service.post.service.PostReadService;
import com.roambupt.service.topic.service.TopicService;
import com.roambupt.service.user.service.UserQueryService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 帖子读服务实现。 */
@Service
public class PostReadServiceImpl implements PostReadService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String STATUS_ONLINE = "ONLINE";
    private static final String ROLE_ADMIN = "ADMIN";

    private final PostMapper postMapper;
    private final PostTopicMapper postTopicMapper;
    private final PostMediaMapper postMediaMapper;
    private final TopicService topicService;
    private final UserQueryService userQueryService;
    private final CommentCountProvider commentCountProvider;

    public PostReadServiceImpl(PostMapper postMapper,
                               PostTopicMapper postTopicMapper,
                               PostMediaMapper postMediaMapper,
                               TopicService topicService,
                               UserQueryService userQueryService,
                               CommentCountProvider commentCountProvider) {
        this.postMapper = postMapper;
        this.postTopicMapper = postTopicMapper;
        this.postMediaMapper = postMediaMapper;
        this.topicService = topicService;
        this.userQueryService = userQueryService;
        this.commentCountProvider = commentCountProvider;
    }

    @Override
    public PostVO detail(long postId, Long viewerUid, String viewerRole) {
        PostDO post = postMapper.selectById(postId);
        if (post == null || !visible(post, viewerUid, viewerRole)) {
            throw new BusinessException(StatusEnum.NOT_FOUND, "帖子不存在");
        }
        return assemble(post, true, commentCountProvider.countByPost(post.getId()));
    }

    @Override
    public PageResult<PostVO> feed(FeedQuery query, Long viewerUid) {
        String feed = StringUtils.defaultIfBlank(query.feed(), "latest").toLowerCase();
        if ("following".equals(feed)) {
            if (viewerUid == null) {
                throw new BusinessException(StatusEnum.UNAUTHORIZED);
            }
            // P1 接关注流，P0 返回空页而不是假装有数据
            return PageResult.of(0, 1, 10, List.of());
        }
        int page = Math.max(1, query.page());
        int size = Math.min(Math.max(1, query.pageSize()), MAX_PAGE_SIZE);
        int offset = (page - 1) * size;

        List<PostDO> posts = "hot".equals(feed)
                ? postMapper.pageOnlineHot(offset, size)
                : postMapper.pageOnline(offset, size);
        long total = postMapper.countOnline();
        Map<Long, Long> commentCounts = commentCountProvider.countByPosts(
                posts.stream().map(PostDO::getId).toList());
        return PageResult.of(total, page, size, posts.stream()
                .map(p -> assemble(p, false, commentCounts.getOrDefault(p.getId(), 0L)))
                .toList());
    }

    @Override
    public PageResult<PostVO> byTopic(long topicId, String sort, int page, int pageSize) {
        // 主题不存在时直接 404，避免返回空列表让人以为「地点还没内容」
        topicService.detail(topicId);
        int pageNo = Math.max(1, page);
        int size = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        int offset = (pageNo - 1) * size;

        List<PostDO> posts = "hot".equalsIgnoreCase(sort)
                ? postMapper.pageByTopicHot(topicId, offset, size)
                : postMapper.pageByTopic(topicId, offset, size);
        long total = postMapper.countByTopic(topicId);
        Map<Long, Long> commentCounts = commentCountProvider.countByPosts(
                posts.stream().map(PostDO::getId).toList());
        return PageResult.of(total, pageNo, size, posts.stream()
                .map(p -> assemble(p, false, commentCounts.getOrDefault(p.getId(), 0L)))
                .toList());
    }

    private boolean visible(PostDO post, Long viewerUid, String viewerRole) {
        if (STATUS_ONLINE.equals(post.getStatus())) {
            return true;
        }
        return viewerUid != null
                && (viewerUid.equals(post.getUserId()) || ROLE_ADMIN.equals(viewerRole));
    }

    private PostVO assemble(PostDO post, boolean withContent, long commentCount) {
        List<PostTopicDO> relations = postTopicMapper.selectList(Wrappers.<PostTopicDO>lambdaQuery()
                .eq(PostTopicDO::getPostId, post.getId())
                .orderByAsc(PostTopicDO::getSortNo));

        Map<Long, TopicBriefVO> briefMap = topicService
                .findBriefs(relations.stream().map(PostTopicDO::getTopicId).toList())
                .stream().collect(Collectors.toMap(TopicBriefVO::id, b -> b, (a, b) -> a));

        List<TopicBriefVO> topics = relations.stream()
                .map(r -> {
                    TopicBriefVO brief = briefMap.get(r.getTopicId());
                    return brief == null ? null : new TopicBriefVO(brief.id(), brief.type(), brief.name(), r.getSortNo());
                })
                .filter(Objects::nonNull)
                // 地点在前并按顺序排，分类在后；前端「地点顺序」直接取 PLACE 序列
                .sorted(Comparator.comparing((TopicBriefVO t) -> "PLACE".equals(t.type()) ? 0 : 1)
                        .thenComparing(t -> t.sortNo() == null ? 0 : t.sortNo()))
                .toList();

        List<MediaVO> media = postMediaMapper.selectList(Wrappers.<PostMediaDO>lambdaQuery()
                        .eq(PostMediaDO::getPostId, post.getId())
                        .orderByAsc(PostMediaDO::getSortNo))
                .stream().map(m -> new MediaVO(m.getMediaType(), m.getUrl(), m.getSortNo())).toList();

        UserBriefVO author = userQueryService.getById(post.getUserId())
                .map(u -> new UserBriefVO(u.id(), u.nickname(), u.avatar(), null))
                .orElse(null);

        return PostConverter.toVO(post, author, topics, media,
                new CountsVO(0, 0, commentCount, 0), withContent);
    }
}
