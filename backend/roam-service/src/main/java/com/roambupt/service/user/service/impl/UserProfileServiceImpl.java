package com.roambupt.service.user.service.impl;

import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.UserProfileVO;
import com.roambupt.api.model.vo.UserVO;
import com.roambupt.service.post.service.PostQueryService;
import com.roambupt.service.user.service.FollowService;
import com.roambupt.service.user.service.UserProfileService;
import com.roambupt.service.user.service.UserQueryService;
import org.springframework.stereotype.Service;

/** 公开主页实现。 */
@Service
public class UserProfileServiceImpl implements UserProfileService {

    private final UserQueryService userQueryService;
    private final FollowService followService;
    private final PostQueryService postQueryService;

    public UserProfileServiceImpl(UserQueryService userQueryService,
                                  FollowService followService,
                                  PostQueryService postQueryService) {
        this.userQueryService = userQueryService;
        this.followService = followService;
        this.postQueryService = postQueryService;
    }

    @Override
    public UserProfileVO profile(long targetUid, Long viewerUid) {
        UserVO user = userQueryService.getById(targetUid)
                .orElseThrow(() -> new BusinessException(StatusEnum.NOT_FOUND, "用户不存在"));
        boolean followed = viewerUid != null && followService.isFollowed(viewerUid, targetUid);
        return new UserProfileVO(user.id(), user.username(), user.nickname(), user.avatar(), user.bio(),
                postQueryService.countOnlineByUser(targetUid),
                followService.countFollowers(targetUid),
                followService.countFollowing(targetUid),
                viewerUid == null ? null : followed);
    }
}
