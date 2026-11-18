package com.roambupt.service.user.service;

/**
 * 用户写服务。注册建号、更新登录时间都走这里，auth 域不直接写 user 表。
 */
public interface UserCommandService {

    /**
     * 建号。用户名重复时抛 {@link org.springframework.dao.DuplicateKeyException}。
     */
    long createAccount(String username, String encodedPassword, String nickname);

    void updateLastLoginTime(long uid);
}
