package com.roambupt.api.model.enums;

import lombok.Getter;

/**
 * 统一返回码。0 表示成功，其余为业务错误码。
 */
@Getter
public enum StatusEnum {

    SUCCESS(0, "ok"),
    PARAM_ERROR(4001, "参数错误"),
    UNAUTHORIZED(401, "未登录或登录已失效"),
    FORBIDDEN(403, "没有权限"),
    NOT_FOUND(404, "内容不存在"),
    CONFLICT(409, "状态冲突"),
    INTERNAL_ERROR(500, "服务器开小差了");

    private final Integer code;
    private final String desc;

    StatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
