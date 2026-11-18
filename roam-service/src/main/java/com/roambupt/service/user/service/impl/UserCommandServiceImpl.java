package com.roambupt.service.user.service.impl;

import com.roambupt.service.user.repository.entity.UserDO;
import com.roambupt.service.user.repository.mapper.UserMapper;
import com.roambupt.service.user.service.UserCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 用户写服务实现。写操作自带事务，auth 域不负责事务边界。
 */
@Service
public class UserCommandServiceImpl implements UserCommandService {

    private static final String DEFAULT_ROLE = "USER";
    private static final int STATUS_NORMAL = 1;

    private final UserMapper userMapper;

    public UserCommandServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long createAccount(String username, String encodedPassword, String nickname) {
        UserDO user = new UserDO();
        user.setUsername(username);
        user.setPassword(encodedPassword);
        user.setNickname(nickname);
        user.setRole(DEFAULT_ROLE);
        user.setStatus(STATUS_NORMAL);
        user.setCreatedTime(LocalDateTime.now());
        user.setUpdatedTime(LocalDateTime.now());
        userMapper.insert(user);
        return user.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLastLoginTime(long uid) {
        UserDO user = new UserDO();
        user.setId(uid);
        user.setUpdatedTime(LocalDateTime.now());
        userMapper.updateById(user);
    }
}
