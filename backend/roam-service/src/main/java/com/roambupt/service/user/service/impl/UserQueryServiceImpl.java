package com.roambupt.service.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.api.model.dto.UserAccount;
import com.roambupt.api.model.vo.UserVO;
import com.roambupt.service.user.converter.UserConverter;
import com.roambupt.service.user.repository.entity.UserDO;
import com.roambupt.service.user.repository.mapper.FollowMapper;
import com.roambupt.service.user.repository.mapper.UserMapper;
import com.roambupt.service.user.service.UserQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 用户读服务实现。
 */
@Service
public class UserQueryServiceImpl implements UserQueryService {

    private final UserMapper userMapper;
    private final FollowMapper followMapper;

    public UserQueryServiceImpl(UserMapper userMapper, FollowMapper followMapper) {
        this.userMapper = userMapper;
        this.followMapper = followMapper;
    }

    @Override
    public Optional<UserAccount> findByUsername(String username) {
        UserDO user = userMapper.selectOne(Wrappers.<UserDO>lambdaQuery().eq(UserDO::getUsername, username));
        return Optional.ofNullable(user).map(UserConverter::toAccount);
    }

    @Override
    public Optional<UserVO> getById(long uid) {
        UserDO user = userMapper.selectById(uid);
        return Optional.ofNullable(user).map(UserConverter::toVO);
    }

    @Override
    public List<Long> findFollowingIds(long uid) {
        return followMapper.findFollowingIds(uid);
    }

    @Override
    public Optional<UserAccount> findAccountById(long uid) {
        UserDO user = userMapper.selectById(uid);
        return Optional.ofNullable(user).map(UserConverter::toAccount);
    }
}
