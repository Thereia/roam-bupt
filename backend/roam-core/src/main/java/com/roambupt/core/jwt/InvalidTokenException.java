package com.roambupt.core.jwt;

/**
 * token 签名错误、过期或格式非法。
 */
public class InvalidTokenException extends RuntimeException {

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
