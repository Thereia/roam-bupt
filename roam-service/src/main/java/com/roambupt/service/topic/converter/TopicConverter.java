package com.roambupt.service.topic.converter;

import com.roambupt.api.model.vo.RatingVO;
import com.roambupt.api.model.vo.TopicBriefVO;
import com.roambupt.api.model.vo.TopicVO;
import com.roambupt.service.topic.repository.entity.TopicDO;

/** 主题转换。 */
public final class TopicConverter {

    private static final RatingVO EMPTY_RATING = new RatingVO(0, 0, 0);

    private TopicConverter() {
    }

    public static TopicVO toVO(TopicDO topic, long postCount) {
        return new TopicVO(topic.getId(), topic.getType(), topic.getName(), topic.getCategory(),
                topic.getCover(), topic.getDescription(), topic.getHeat(), topic.getNodeId(),
                postCount, 0L, EMPTY_RATING);
    }

    public static TopicBriefVO toBrief(TopicDO topic, Integer sortNo) {
        return new TopicBriefVO(topic.getId(), topic.getType(), topic.getName(), sortNo);
    }
}
