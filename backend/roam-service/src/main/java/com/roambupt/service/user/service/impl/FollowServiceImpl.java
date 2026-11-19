package com.roambupt.service.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.FollowResultVO;
import com.roambupt.service.user.repository.entity.FollowDO;
import com.roambupt.service.user.repository.mapper.FollowMapper;
import com.roambupt.service.user.service.FollowService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 关注实现：幂等靠唯一键，重复关注不报错。 */
@Service
public class FollowServiceImpl implements FollowService {

    private final FollowMapper followMapper;

    public FollowServiceImpl(FollowMapper followMapper) {
        this.followMapper = followMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowResultVO follow(long uid, long targetUid) {
        if (uid == targetUid) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "不能关注自己");
        }
        try {
            FollowDO follow = new FollowDO();
            follow.setUserId(uid);
            follow.setTargetUserId(targetUid);
            follow.setCreatedTime(LocalDateTime.now());
            followMapper.insert(follow);
        } catch (DuplicateKeyException e) {
            // 已关注，按幂等成功处理
        }
        return new FollowResultVO(true, followMapper.countFollowers(targetUid));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowResultVO unfollow(long uid, long targetUid) {
        followMapper.delete(Wrappers.<FollowDO>lambdaQuery()
                .eq(FollowDO::getUserId, uid)
                .eq(FollowDO::getTargetUserId, targetUid));
        return new FollowResultVO(false, followMapper.countFollowers(targetUid));
    }

    @Override
    public boolean isFollowed(long uid, long targetUid) {
        return followMapper.selectCount(Wrappers.<FollowDO>lambdaQuery()
                .eq(FollowDO::getUserId, uid)
                .eq(FollowDO::getTargetUserId, targetUid)) > 0;
    }

    @Override
    public long countFollowers(long uid) {
        return followMapper.countFollowers(uid);
    }

    @Override
    public long countFollowing(long uid) {
        return followMapper.countFollowing(uid);
    }

    @Override
    public List<Long> findFollowingIds(long uid) {
        return followMapper.findFollowingIds(uid);
    }
}
