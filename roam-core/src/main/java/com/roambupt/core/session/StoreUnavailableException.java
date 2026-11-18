package com.roambupt.core.session;

/**
 * 会话存储不可用（Redis 连接失败等）。由调用方翻译成 503。
 */
public class StoreUnavailableException extends RuntimeException {

    public StoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
