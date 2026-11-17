package com.roambupt.api.model.vo;

import com.roambupt.api.model.enums.StatusEnum;
import lombok.Data;

import java.io.Serializable;

/**
 * 统一返回结构。Controller 一律返回它，前端按 code 判断成功与否。
 */
@Data
public class ResVo<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer code;
    private String message;
    private T data;

    public static <T> ResVo<T> ok(T data) {
        ResVo<T> vo = new ResVo<>();
        vo.setCode(StatusEnum.SUCCESS.getCode());
        vo.setMessage(StatusEnum.SUCCESS.getDesc());
        vo.setData(data);
        return vo;
    }

    public static ResVo<Void> ok() {
        return ok(null);
    }

    public static <T> ResVo<T> fail(StatusEnum status) {
        return fail(status.getCode(), status.getDesc());
    }

    public static <T> ResVo<T> fail(Integer code, String message) {
        ResVo<T> vo = new ResVo<>();
        vo.setCode(code);
        vo.setMessage(message);
        return vo;
    }
}
