package com.roambupt.api.model.vo;

/**
 * 对外的用户信息，不含密码。
 */
public record UserVO(Long id, String username, String nickname, String avatar, String bio,
                     String role, long postCount, long followerCount, long followingCount) {
}
