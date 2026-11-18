package com.roambupt.api.model.exception;

import com.roambupt.api.model.enums.StatusEnum;
import lombok.Getter;

/**
 * 业务异常：由全局异常处理器翻译成 ResVo，不打印堆栈。
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Integer code;

    public BusinessException(StatusEnum status) {
        super(status.getDesc());
        this.code = status.getCode();
    }

    public BusinessException(StatusEnum status, String message) {
        super(message);
        this.code = status.getCode();
    }
}
