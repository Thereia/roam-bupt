package com.roambupt.service.user.service;

import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.AdminUserVO;

/** 后台用户管理。 */
public interface UserAdminService {

    PageResult<AdminUserVO> page(String keyword, int page, int pageSize);

    /** status: 1 正常 / 0 封禁。封禁时撤销该用户全部会话。 */
    void changeStatus(long targetUid, int status);
}
