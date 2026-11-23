package com.roambupt.service.user.service;

import com.roambupt.api.model.vo.FollowResultVO;

import java.util.List;

/** 关注关系。全部幂等。 */
public interface FollowService {

    FollowResultVO follow(long uid, long targetUid);

    FollowResultVO unfollow(long uid, long targetUid);

    boolean isFollowed(long uid, long targetUid);

    long countFollowers(long uid);

    long countFollowing(long uid);

    List<Long> findFollowingIds(long uid);

    /** 粉丝 id（按关注时间倒序，分页）。 */
    List<Long> followerIds(long uid, int page, int pageSize);

    /** 关注的人 id（分页）。 */
    List<Long> followingIds(long uid, int page, int pageSize);
}
