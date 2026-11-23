package com.roambupt.service.rating.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.RatingSummaryVO;
import com.roambupt.api.model.vo.RatingVO;
import com.roambupt.service.rating.repository.entity.TopicRatingDO;
import com.roambupt.service.rating.repository.mapper.TopicRatingMapper;
import com.roambupt.service.rating.service.RatingService;
import com.roambupt.service.topic.service.TopicService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/** 评分实现：一人一地点一行，重复打分即改分；均分用贝叶斯平滑现算。 */
@Service
public class RatingServiceImpl implements RatingService {

    /** 先验条数与先验均值：1 条 5 星不能排到 100 条 4.2 星前面。 */
    private static final int PRIOR_COUNT = 10;
    private static final double PRIOR_MEAN = 3.5;

    private final TopicRatingMapper ratingMapper;
    private final TopicService topicService;

    public RatingServiceImpl(TopicRatingMapper ratingMapper, TopicService topicService) {
        this.ratingMapper = ratingMapper;
        this.topicService = topicService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RatingSummaryVO rate(long topicId, long uid, int score) {
        if (score < 1 || score > 5) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "评分只能是 1 到 5");
        }
        requirePlace(topicId);

        TopicRatingDO existing = ratingMapper.selectOne(Wrappers.<TopicRatingDO>lambdaQuery()
                .eq(TopicRatingDO::getUserId, uid)
                .eq(TopicRatingDO::getTopicId, topicId));
        if (existing != null) {
            ratingMapper.update(null, Wrappers.<TopicRatingDO>lambdaUpdate()
                    .eq(TopicRatingDO::getId, existing.getId())
                    .set(TopicRatingDO::getScore, score)
                    .set(TopicRatingDO::getUpdatedTime, LocalDateTime.now()));
        } else {
            TopicRatingDO row = new TopicRatingDO();
            row.setUserId(uid);
            row.setTopicId(topicId);
            row.setScore(score);
            row.setCreatedTime(LocalDateTime.now());
            row.setUpdatedTime(LocalDateTime.now());
            try {
                ratingMapper.insert(row);
            } catch (DuplicateKeyException e) {
                // 并发首次打分：唯一键兜底，按改分处理
                ratingMapper.update(null, Wrappers.<TopicRatingDO>lambdaUpdate()
                        .eq(TopicRatingDO::getUserId, uid)
                        .eq(TopicRatingDO::getTopicId, topicId)
                        .set(TopicRatingDO::getScore, score));
            }
        }
        return summary(topicId, uid);
    }

    @Override
    public RatingSummaryVO summary(long topicId, Long viewerUid) {
        long count = ratingMapper.countByTopic(topicId);
        long sum = ratingMapper.sumByTopic(topicId);
        int[] distribution = new int[5];
        Map<Integer, Long> grouped = ratingMapper.distribution(topicId).stream()
                .collect(Collectors.toMap(TopicRatingMapper.ScoreRow::getScore,
                        TopicRatingMapper.ScoreRow::getC));
        for (int score = 1; score <= 5; score++) {
            distribution[score - 1] = grouped.getOrDefault(score, 0L).intValue();
        }
        Integer myScore = null;
        if (viewerUid != null) {
            TopicRatingDO mine = ratingMapper.selectOne(Wrappers.<TopicRatingDO>lambdaQuery()
                    .eq(TopicRatingDO::getUserId, viewerUid)
                    .eq(TopicRatingDO::getTopicId, topicId));
            myScore = mine == null ? null : mine.getScore();
        }
        return new RatingSummaryVO(topicId, count, bayesian(count, sum), myScore, distribution);
    }

    @Override
    public RatingVO ratingOf(long topicId) {
        long count = ratingMapper.countByTopic(topicId);
        long sum = ratingMapper.sumByTopic(topicId);
        double avg = bayesian(count, sum);
        return new RatingVO(avg, Math.round(avg * 10) / 10.0, count);
    }

    @Override
    public java.util.Map<Long, RatingVO> ratingsOf(java.util.Collection<Long> topicIds) {
        java.util.Map<Long, RatingVO> result = new java.util.HashMap<>();
        if (topicIds == null || topicIds.isEmpty()) {
            return result;
        }
        for (TopicRatingMapper.TopicStatRow row : ratingMapper.statsByTopics(topicIds)) {
            double avg = bayesian(row.getC(), row.getS());
            result.put(row.getTopicId(), new RatingVO(avg, Math.round(avg * 10) / 10.0, row.getC()));
        }
        return result;
    }

    private double bayesian(long count, long sum) {
        return (PRIOR_COUNT * PRIOR_MEAN + sum) / (PRIOR_COUNT + count);
    }

    private void requirePlace(long topicId) {
        if (!topicService.isEnabledPlace(topicId)) {
            throw new BusinessException(StatusEnum.NOT_FOUND, "地点不存在");
        }
    }
}
