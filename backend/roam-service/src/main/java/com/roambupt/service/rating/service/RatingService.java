package com.roambupt.service.rating.service;

import com.roambupt.api.model.vo.RatingSummaryVO;
import com.roambupt.api.model.vo.RatingVO;

/** 地点评分。 */
public interface RatingService {

    /** 打分或改分，返回最新汇总（含 myScore）。 */
    RatingSummaryVO rate(long topicId, long uid, int score);

    RatingSummaryVO summary(long topicId, Long viewerUid);

    /** 给主题详情填充 avg/count。 */
    RatingVO ratingOf(long topicId);

    /** 给列表批量填充 avg/count。 */
    java.util.Map<Long, RatingVO> ratingsOf(java.util.Collection<Long> topicIds);
}
