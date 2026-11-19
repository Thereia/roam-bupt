package com.roambupt.api.model.vo;

/** 后台用户行：带账号状态。 */
public record AdminUserVO(Long id, String username, String nickname, int status,
                          long postCount, long followerCount, long followingCount) {
}
