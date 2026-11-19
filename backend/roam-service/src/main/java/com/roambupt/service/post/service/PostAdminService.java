package com.roambupt.service.post.service;

import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.PostVO;

/** 后台帖子操作。 */
public interface PostAdminService {

    PageResult<PostVO> listByStatus(String status, int page, int pageSize);

    /** 按状态机校验后改状态；非法迁移 409。 */
    void changeStatus(long postId, String targetStatus);
}
