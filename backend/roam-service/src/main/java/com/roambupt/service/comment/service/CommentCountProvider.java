package com.roambupt.service.comment.service;

import java.util.Collection;
import java.util.Map;

/** 评论计数，供帖子展示使用。 */
public interface CommentCountProvider {

    long countByPost(long postId);

    Map<Long, Long> countByPosts(Collection<Long> postIds);
}
