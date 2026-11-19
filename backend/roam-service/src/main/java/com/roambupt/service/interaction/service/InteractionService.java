package com.roambupt.service.interaction.service;

import com.roambupt.api.model.vo.InteractionResultVO;

/** 点赞/收藏。全部幂等：重复点、取消未点过的对象都返回成功。 */
public interface InteractionService {

    InteractionResultVO like(long postId, long uid);

    InteractionResultVO unlike(long postId, long uid);

    InteractionResultVO collect(long postId, long uid);

    InteractionResultVO uncollect(long postId, long uid);
}
