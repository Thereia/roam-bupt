package com.roambupt.service.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.api.model.dto.UserAccount;
import com.roambupt.api.model.vo.UserVO;
import com.roambupt.service.user.converter.UserConverter;
import com.roambupt.service.user.repository.entity.UserDO;
import com.roambupt.service.user.repository.mapper.UserMapper;
import com.roambupt.service.user.service.UserQueryService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 用户读服务实现。
 */
@Service
public class UserQueryServiceImpl implements UserQueryService {

    private final UserMapper userMapper;

    public UserQueryServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
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
    public Optional<UserAccount> findAccountById(long uid) {
        UserDO user = userMapper.selectById(uid);
        return Optional.ofNullable(user).map(UserConverter::toAccount);
    }
}
