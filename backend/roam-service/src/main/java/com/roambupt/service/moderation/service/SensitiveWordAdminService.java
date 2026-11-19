package com.roambupt.service.moderation.service;

import com.roambupt.api.model.vo.SensitiveWordVO;
import com.roambupt.api.model.vo.PageResult;

/** 敏感词维护（后台）。 */
public interface SensitiveWordAdminService {

    PageResult<SensitiveWordVO> page(int page, int pageSize);

    void add(String word);

    void remove(long id);
}
