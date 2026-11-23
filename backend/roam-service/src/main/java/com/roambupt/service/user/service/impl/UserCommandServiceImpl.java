package com.roambupt.service.user.service.impl;

import com.roambupt.api.model.dto.ProfileUpdateCommand;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
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
    private static final int NICKNAME_MAX = 32;
    private static final int BIO_MAX = 120;
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
    public void updateProfile(long uid, ProfileUpdateCommand command) {
        String nickname = command.nickname() == null ? "" : command.nickname().trim();
        if (nickname.isEmpty() || nickname.length() > NICKNAME_MAX) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "昵称需要 1 到 32 个字");
        }
        String bio = command.bio() == null ? null : command.bio().trim();
        if (bio != null && bio.length() > BIO_MAX) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "简介最多 120 字");
        }
        String avatar = command.avatar();
        if (avatar != null && !avatar.isBlank() && !avatar.startsWith("/files/")) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "头像地址不合法");
        }

        UserDO user = userMapper.selectById(uid);
        if (user == null) {
            throw new BusinessException(StatusEnum.NOT_FOUND, "用户不存在");
        }
        // updateById 默认跳过 null 字段，清空简介/头像必须显式 set
        userMapper.update(null, com.baomidou.mybatisplus.core.toolkit.Wrappers.<UserDO>lambdaUpdate()
                .eq(UserDO::getId, uid)
                .set(UserDO::getNickname, nickname)
                .set(UserDO::getAvatar, avatar == null || avatar.isBlank() ? null : avatar)
                .set(UserDO::getBio, bio == null || bio.isEmpty() ? null : bio)
                .set(UserDO::getUpdatedTime, LocalDateTime.now()));
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
