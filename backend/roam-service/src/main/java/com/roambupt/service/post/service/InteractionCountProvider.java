package com.roambupt.service.post.service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/** 帖子互动数据，供详情/列表填充 counts 与 myLiked/myCollected。 */
public interface InteractionCountProvider {

    Summary summarize(Collection<Long> postIds, Long viewerUid);

    /**
     * @param likes            postId -> 点赞数
     * @param collects         postId -> 收藏数
     * @param likedByViewer    viewer 点过赞的 postId
     * @param collectedByViewer viewer 收藏过的 postId
     */
    record Summary(Map<Long, Long> likes, Map<Long, Long> collects,
                   Set<Long> likedByViewer, Set<Long> collectedByViewer) {

        public static Summary empty() {
            return new Summary(Map.of(), Map.of(), Set.of(), Set.of());
        }
    }
}
