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
import com.roambupt.core.counter.CounterStore;
import com.roambupt.service.comment.service.CommentCountProvider;
import com.roambupt.service.post.service.InteractionCountProvider;
import com.roambupt.service.post.service.PostReadService;
import com.roambupt.service.topic.service.TopicService;
import com.roambupt.service.user.service.UserQueryService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.time.Duration;
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
    private final InteractionCountProvider interactionCountProvider;
    private final CounterStore counterStore;

    public PostReadServiceImpl(PostMapper postMapper,
                               PostTopicMapper postTopicMapper,
                               PostMediaMapper postMediaMapper,
                               TopicService topicService,
                               UserQueryService userQueryService,
                               CommentCountProvider commentCountProvider,
                               InteractionCountProvider interactionCountProvider,
                               CounterStore counterStore) {
        this.postMapper = postMapper;
        this.postTopicMapper = postTopicMapper;
        this.postMediaMapper = postMediaMapper;
        this.topicService = topicService;
        this.userQueryService = userQueryService;
        this.commentCountProvider = commentCountProvider;
        this.interactionCountProvider = interactionCountProvider;
        this.counterStore = counterStore;
    }

    @Override
    public PostVO detail(long postId, Long viewerUid, String viewerRole) {
        PostDO post = postMapper.selectById(postId);
        if (post == null || !visible(post, viewerUid, viewerRole)) {
            throw new BusinessException(StatusEnum.NOT_FOUND, "帖子不存在");
        }
        var summary = interactionCountProvider.summarize(List.of(post.getId()), viewerUid);
        long views = counterStore.increment(viewKey(post.getId()), Duration.ofDays(365));
        return assemble(post, true, commentCountProvider.countByPost(post.getId()), summary, viewerUid, views);
    }

    @Override
    public PageResult<PostVO> feed(FeedQuery query, Long viewerUid) {
        String feed = StringUtils.defaultIfBlank(query.feed(), "latest").toLowerCase();
        if ("following".equals(feed)) {
            if (viewerUid == null) {
                throw new BusinessException(StatusEnum.UNAUTHORIZED);
            }
            List<Long> followingIds = userQueryService.findFollowingIds(viewerUid);
            int followingPage = Math.max(1, query.page());
            int followingSize = Math.min(Math.max(1, query.pageSize()), MAX_PAGE_SIZE);
            if (followingIds.isEmpty()) {
                return PageResult.of(0, followingPage, followingSize, List.of());
            }
            List<PostDO> followingPosts = postMapper.pageByUserIds(followingIds,
                    (followingPage - 1) * followingSize, followingSize);
            long followingTotal = postMapper.countByUserIds(followingIds);
            List<Long> followingPostIds = followingPosts.stream().map(PostDO::getId).toList();
            Map<Long, Long> followingComments = commentCountProvider.countByPosts(followingPostIds);
            var followingSummary = interactionCountProvider.summarize(followingPostIds, viewerUid);
            Map<Long, Long> followingViews = viewsOf(followingPostIds);
            return PageResult.of(followingTotal, followingPage, followingSize, followingPosts.stream()
                    .map(p -> assemble(p, false, followingComments.getOrDefault(p.getId(), 0L),
                            followingSummary, viewerUid, followingViews.getOrDefault(p.getId(), 0L)))
                    .toList());
        }
        int page = Math.max(1, query.page());
        int size = Math.min(Math.max(1, query.pageSize()), MAX_PAGE_SIZE);
        int offset = (page - 1) * size;

        Long topicId = query.topicId();
        if (topicId != null && topicService.findEnabledIds(List.of(topicId)).isEmpty()) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "主题不存在或已停用");
        }
        List<PostDO> posts;
        long total;
        if (topicId != null) {
            posts = switch (feed) {
                case "recommend" -> postMapper.pageByTopicRecommended(topicId, offset, size);
                case "hot" -> postMapper.pageByTopicHot(topicId, offset, size);
                default -> postMapper.pageByTopic(topicId, offset, size);
            };
            total = postMapper.countByTopic(topicId);
        } else {
            posts = switch (feed) {
                case "recommend" -> postMapper.pageOnlineRecommended(offset, size);
                case "hot" -> postMapper.pageOnlineHot(offset, size);
                default -> postMapper.pageOnline(offset, size);
            };
            total = postMapper.countOnline();
        }
        List<Long> ids = posts.stream().map(PostDO::getId).toList();
        Map<Long, Long> commentCounts = commentCountProvider.countByPosts(ids);
        var summary = interactionCountProvider.summarize(ids, viewerUid);
        Map<Long, Long> views = viewsOf(ids);
        return PageResult.of(total, page, size, posts.stream()
                .map(p -> assemble(p, false, commentCounts.getOrDefault(p.getId(), 0L), summary, viewerUid,
                        views.getOrDefault(p.getId(), 0L)))
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
        List<Long> ids = posts.stream().map(PostDO::getId).toList();
        Map<Long, Long> commentCounts = commentCountProvider.countByPosts(ids);
        var summary = interactionCountProvider.summarize(ids, null);
        Map<Long, Long> views = viewsOf(ids);
        return PageResult.of(total, pageNo, size, posts.stream()
                .map(p -> assemble(p, false, commentCounts.getOrDefault(p.getId(), 0L), summary, null,
                        views.getOrDefault(p.getId(), 0L)))
                .toList());
    }

    @Override
    public List<PostVO> assembleAll(List<PostDO> posts, Long viewerUid) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }
        List<Long> ids = posts.stream().map(PostDO::getId).toList();
        Map<Long, Long> comments = commentCountProvider.countByPosts(ids);
        var summary = interactionCountProvider.summarize(ids, viewerUid);
        Map<Long, Long> views = viewsOf(ids);
        return posts.stream()
                .map(p -> assemble(p, false, comments.getOrDefault(p.getId(), 0L), summary, viewerUid,
                        views.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    @Override
    public PageResult<PostVO> byUser(long uid, Long viewerUid, int page, int pageSize) {
        int pageNo = Math.max(1, page);
        int size = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        boolean includeAll = viewerUid != null && viewerUid == uid;
        List<PostDO> posts = postMapper.pageByUser(uid, includeAll, (pageNo - 1) * size, size);
        long total = postMapper.countByUser(uid, includeAll);
        List<Long> ids = posts.stream().map(PostDO::getId).toList();
        Map<Long, Long> comments = commentCountProvider.countByPosts(ids);
        var summary = interactionCountProvider.summarize(ids, viewerUid);
        Map<Long, Long> views = viewsOf(ids);
        return PageResult.of(total, pageNo, size, posts.stream()
                .map(p -> assemble(p, false, comments.getOrDefault(p.getId(), 0L), summary, viewerUid,
                        views.getOrDefault(p.getId(), 0L)))
                .toList());
    }

    @Override
    public List<PostVO> byIds(List<Long> ids, Long viewerUid) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, PostDO> byId = postMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(PostDO::getId, p -> p, (a, b) -> a));
        List<PostDO> ordered = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .filter(post -> STATUS_ONLINE.equals(post.getStatus()))
                .toList();
        if (ordered.isEmpty()) {
            return List.of();
        }
        List<Long> presentIds = ordered.stream().map(PostDO::getId).toList();
        Map<Long, Long> commentCounts = commentCountProvider.countByPosts(presentIds);
        var summary = interactionCountProvider.summarize(presentIds, viewerUid);
        Map<Long, Long> views = viewsOf(presentIds);
        return ordered.stream()
                .map(p -> assemble(p, false, commentCounts.getOrDefault(p.getId(), 0L), summary, viewerUid,
                        views.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    private String viewKey(long postId) {
        return "roam:post:view:" + postId;
    }

    private Map<Long, Long> viewsOf(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<String> keys = ids.stream().map(this::viewKey).toList();
        Map<String, Long> raw = counterStore.multiGet(keys);
        Map<Long, Long> views = new java.util.HashMap<>();
        for (Long id : ids) {
            views.put(id, raw.getOrDefault(viewKey(id), 0L));
        }
        return views;
    }

    private boolean visible(PostDO post, Long viewerUid, String viewerRole) {
        // 已删除的帖子对任何人都不可见（作者与管理员也一样）
        if ("DELETED".equals(post.getStatus())) {
            return false;
        }
        if (STATUS_ONLINE.equals(post.getStatus())) {
            return true;
        }
        return viewerUid != null
                && (viewerUid.equals(post.getUserId()) || ROLE_ADMIN.equals(viewerRole));
    }

    private PostVO assemble(PostDO post, boolean withContent, long commentCount,
                            InteractionCountProvider.Summary summary, Long viewerUid, long viewCount) {
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

        long likes = summary.likes().getOrDefault(post.getId(), 0L);
        long collects = summary.collects().getOrDefault(post.getId(), 0L);
        Boolean myLiked = viewerUid == null ? null : summary.likedByViewer().contains(post.getId());
        Boolean myCollected = viewerUid == null ? null : summary.collectedByViewer().contains(post.getId());
        return PostConverter.toVO(post, author, topics, media,
                new CountsVO(likes, collects, commentCount, viewCount), withContent, myLiked, myCollected);
    }
}
