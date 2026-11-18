package com.roambupt.core.context;

import com.roambupt.api.model.vo.ReqInfo;

/**
 * 当前请求用户上下文。由 web 层拦截器 set/clear，业务层只读。
 */
public final class CurrentUserContext {

    private static final ThreadLocal<ReqInfo> HOLDER = new ThreadLocal<>();

    private CurrentUserContext() {
    }

    public static void set(ReqInfo info) {
        HOLDER.set(info);
    }

    public static ReqInfo get() {
        return HOLDER.get();
    }

    public static Long uid() {
        ReqInfo info = HOLDER.get();
        return info == null ? null : info.uid();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
