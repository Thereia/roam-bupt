package com.roambupt.api.model.vo;

import java.util.List;

/** 统一分页返回。 */
public record PageResult<T>(long total, int page, int pageSize, List<T> list) {

    public static <T> PageResult<T> of(long total, int page, int pageSize, List<T> list) {
        return new PageResult<>(total, page, pageSize, list);
    }
}
