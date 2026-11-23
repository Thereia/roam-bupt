package com.roambupt.service.post.service;

import java.util.List;

/** 帖子读能力，供其他域使用（评论判断帖子是否存在）。 */
public interface PostQueryService {

    boolean existsOnline(long postId);

    /** 帖子作者；帖子不存在或已删返回 null。 */
    Long findAuthorId(long postId);

    /** 兜底搜索：标题/正文 LIKE，返回帖子 id。 */
    List<Long> searchIds(String keyword, Long topicId, int offset, int size);

    long countSearch(String keyword, Long topicId);

    /** 用户在线帖子数。 */
    long countOnlineByUser(long uid);

    /** 帖子标题；不存在返回 null。 */
    String findTitle(long postId);

    /** 批量取标题，供通知列表展示。 */
    java.util.Map<Long, String> titlesOf(java.util.Collection<Long> postIds);

    /** 帖子状态；不存在返回 null。 */
    String findStatus(long postId);

    /** 帖子挂的地点主题 id，按 sort_no 升序；没有地点返回空列表。 */
    List<Long> findOrderedPlaceTopicIds(long postId);
}
