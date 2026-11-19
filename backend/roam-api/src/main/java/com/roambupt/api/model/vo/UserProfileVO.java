package com.roambupt.api.model.vo;

/** 公开主页。 */
public record UserProfileVO(Long id, String username, String nickname, String avatar, String bio,
                            long postCount, long followerCount, long followingCount,
                            Boolean isFollowed) {
}
