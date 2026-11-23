package com.roambupt.service.post.service;

import com.roambupt.api.model.dto.PostCreateCommand;
import com.roambupt.api.model.vo.PostCreatedVO;

/** 帖子写服务。 */
public interface PostWriteService {

    PostCreatedVO create(PostCreateCommand command, long uid);

    /** 作者编辑帖子：重新校验、重挂主题与图片，并按配置决定是否回到待审。 */
    PostCreatedVO update(long postId, PostCreateCommand command, long uid);

    /** 作者把草稿发布出去：DRAFT → PENDING/ONLINE。 */
    PostCreatedVO publishDraft(long postId, long uid);

    /** 作者或管理员删除（软删，置 DELETED）。 */
    void delete(long postId, long uid, String role);
}
