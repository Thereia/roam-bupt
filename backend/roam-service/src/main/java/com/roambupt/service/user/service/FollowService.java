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
}
