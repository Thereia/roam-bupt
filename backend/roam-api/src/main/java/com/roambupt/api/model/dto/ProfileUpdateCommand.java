package com.roambupt.api.model.dto;

/** 修改个人资料：昵称必填，头像与简介可空。 */
public record ProfileUpdateCommand(String nickname, String avatar, String bio) {
}
