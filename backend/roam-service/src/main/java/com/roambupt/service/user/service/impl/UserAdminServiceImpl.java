package com.roambupt.service.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.AdminUserVO;
import com.roambupt.core.session.SessionStore;
import com.roambupt.service.post.service.PostQueryService;
import com.roambupt.service.user.repository.entity.UserDO;
import com.roambupt.service.user.repository.mapper.UserMapper;
import com.roambupt.service.user.service.FollowService;
import com.roambupt.service.user.service.UserAdminService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 后台用户管理实现。封禁时撤销该用户全部会话。 */
@Service
public class UserAdminServiceImpl implements UserAdminService {

    private static final int MAX_PAGE_SIZE = 50;

    private final UserMapper userMapper;
    private final PostQueryService postQueryService;
    private final FollowService followService;
    private final SessionStore sessionStore;

    public UserAdminServiceImpl(UserMapper userMapper,
                                PostQueryService postQueryService,
                                FollowService followService,
                                SessionStore sessionStore) {
        this.userMapper = userMapper;
        this.postQueryService = postQueryService;
        this.followService = followService;
        this.sessionStore = sessionStore;
    }

    @Override
    public PageResult<AdminUserVO> page(String keyword, int page, int pageSize) {
        int pageNo = Math.max(1, page);
        int size = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        var wrapper = Wrappers.<UserDO>lambdaQuery()
                .and(StringUtils.isNotBlank(keyword), w -> w
                        .like(UserDO::getUsername, keyword)
                        .or()
                        .like(UserDO::getNickname, keyword))
                .orderByDesc(UserDO::getId)
                .last("LIMIT " + ((pageNo - 1) * size) + ", " + size);

        List<AdminUserVO> list = userMapper.selectList(wrapper).stream()
                .map(user -> new AdminUserVO(user.getId(), user.getUsername(), user.getNickname(),
                        user.getStatus() == null ? 1 : user.getStatus(),
                        postQueryService.countOnlineByUser(user.getId()),
                        followService.countFollowers(user.getId()),
                        followService.countFollowing(user.getId())))
                .toList();
        long total = userMapper.selectCount(Wrappers.<UserDO>lambdaQuery()
                .and(StringUtils.isNotBlank(keyword), w -> w
                        .like(UserDO::getUsername, keyword)
                        .or()
                        .like(UserDO::getNickname, keyword)));
        return PageResult.of(total, pageNo, size, list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeStatus(long targetUid, int status) {
        UserDO user = userMapper.selectById(targetUid);
        if (user == null) {
            throw new BusinessException(StatusEnum.NOT_FOUND, "用户不存在");
        }
        userMapper.update(null, Wrappers.<UserDO>lambdaUpdate()
                .eq(UserDO::getId, targetUid)
                .set(UserDO::getStatus, status));
        if (status == 0) {
            // 封禁立即生效：旧 token 下次请求就 401
            sessionStore.revokeAllOf(targetUid);
        }
    }
}
