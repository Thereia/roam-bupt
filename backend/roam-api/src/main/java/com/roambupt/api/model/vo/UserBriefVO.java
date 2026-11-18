package com.roambupt.api.model.vo;

/** 帖子里的作者简要信息。 */
public record UserBriefVO(Long id, String nickname, String avatar, Boolean followed) {
}
