package com.roambupt.service.post.service;

import com.roambupt.api.model.dto.PostCreateCommand;
import com.roambupt.api.model.vo.PostCreatedVO;

/** 帖子写服务。 */
public interface PostWriteService {

    PostCreatedVO create(PostCreateCommand command, long uid);
}
