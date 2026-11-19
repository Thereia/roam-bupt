package com.roambupt.service.post.service;

/** 帖子读能力，供其他域使用（评论判断帖子是否存在）。 */
public interface PostQueryService {

    boolean existsOnline(long postId);
}
