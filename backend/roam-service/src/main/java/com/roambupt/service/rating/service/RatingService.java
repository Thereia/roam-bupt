package com.roambupt.service.rating.service;

import com.roambupt.api.model.dto.RatingCommand;
import com.roambupt.api.model.vo.RatingFeedVO;
import com.roambupt.api.model.vo.RatingSummaryVO;
import com.roambupt.api.model.vo.RatingVO;

/** 地点评分与评价流。 */
public interface RatingService {

    /** 提交或修改评价（星必填，文字图片可选），返回最新汇总（含 myScore）。 */
    RatingSummaryVO rate(long topicId, long uid, RatingCommand command);

    RatingSummaryVO summary(long topicId, Long viewerUid);

    /** 评价流：汇总 + 按时间倒序的分页列表。 */
    RatingFeedVO feed(long topicId, Long viewerUid, int page, int pageSize);

    /** 给主题详情填充 avg/count。 */
    RatingVO ratingOf(long topicId);

    /** 删除自己的评价。 */
    RatingSummaryVO clear(long topicId, long uid);

    /** 给列表批量填充 avg/count。 */
    java.util.Map<Long, RatingVO> ratingsOf(java.util.Collection<Long> topicIds);
}
