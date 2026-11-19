package com.roambupt.service.interaction.service.impl;

import com.roambupt.service.interaction.repository.mapper.UserFootMapper;
import com.roambupt.service.post.service.InteractionCountProvider;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 互动数据汇总：计数与「我点过没有」。 */
@Service
public class InteractionCountProviderImpl implements InteractionCountProvider {

    private static final String LIKE = "LIKE";
    private static final String COLLECT = "COLLECT";

    private final UserFootMapper userFootMapper;

    public InteractionCountProviderImpl(UserFootMapper userFootMapper) {
        this.userFootMapper = userFootMapper;
    }

    @Override
    public Summary summarize(Collection<Long> postIds, Long viewerUid) {
        if (postIds == null || postIds.isEmpty()) {
            return Summary.empty();
        }
        Map<Long, Long> likes = toMap(userFootMapper.countByPosts(LIKE, postIds));
        Map<Long, Long> collects = toMap(userFootMapper.countByPosts(COLLECT, postIds));
        Set<Long> liked = viewerUid == null
                ? Set.of()
                : new HashSet<>(userFootMapper.findPostIds(viewerUid, LIKE, postIds));
        Set<Long> collected = viewerUid == null
                ? Set.of()
                : new HashSet<>(userFootMapper.findPostIds(viewerUid, COLLECT, postIds));
        return new Summary(likes, collects, liked, collected);
    }

    private Map<Long, Long> toMap(java.util.List<UserFootMapper.FootCountRow> rows) {
        return rows.stream().collect(Collectors.toMap(UserFootMapper.FootCountRow::getPostId,
                UserFootMapper.FootCountRow::getC));
    }
}
