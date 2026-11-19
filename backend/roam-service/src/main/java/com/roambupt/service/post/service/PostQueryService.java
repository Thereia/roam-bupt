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
}
