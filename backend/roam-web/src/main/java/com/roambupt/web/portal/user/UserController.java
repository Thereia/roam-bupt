package com.roambupt.web.portal.user;

import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.FollowResultVO;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.PostVO;
import com.roambupt.api.model.vo.ResVo;
import com.roambupt.api.model.vo.UserProfileVO;
import com.roambupt.core.context.CurrentUserContext;
import com.roambupt.service.post.service.PostReadService;
import com.roambupt.service.user.service.FollowService;
import com.roambupt.service.user.service.UserProfileService;
import com.roambupt.service.user.service.UserQueryService;
import com.roambupt.web.global.OptionalAuth;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 个人主页与关注。 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserProfileService userProfileService;
    private final UserQueryService userQueryService;
    private final FollowService followService;
    private final PostReadService postReadService;

    public UserController(UserProfileService userProfileService,
                          UserQueryService userQueryService,
                          FollowService followService,
                          PostReadService postReadService) {
        this.userProfileService = userProfileService;
        this.userQueryService = userQueryService;
        this.followService = followService;
        this.postReadService = postReadService;
    }

    @OptionalAuth
    @GetMapping("/{id}")
    public ResVo<UserProfileVO> profile(@PathVariable long id) {
        return ResVo.ok(userProfileService.profile(id, CurrentUserContext.uid()));
    }

    @OptionalAuth
    @GetMapping("/{id}/posts")
    public ResVo<PageResult<PostVO>> posts(@PathVariable long id,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int pageSize) {
        return ResVo.ok(postReadService.byUser(id, CurrentUserContext.uid(), page, pageSize));
    }

    @PostMapping("/{id}/follow")
    public ResVo<FollowResultVO> follow(@PathVariable long id) {
        long uid = uid();
        requireUser(id);
        return ResVo.ok(followService.follow(uid, id));
    }

    @DeleteMapping("/{id}/follow")
    public ResVo<FollowResultVO> unfollow(@PathVariable long id) {
        long uid = uid();
        requireUser(id);
        return ResVo.ok(followService.unfollow(uid, id));
    }

    private void requireUser(long id) {
        if (userQueryService.getById(id).isEmpty()) {
            throw new BusinessException(StatusEnum.NOT_FOUND, "用户不存在");
        }
    }

    private long uid() {
        Long uid = CurrentUserContext.uid();
        if (uid == null) {
            throw new BusinessException(StatusEnum.UNAUTHORIZED);
        }
        return uid;
    }
}
