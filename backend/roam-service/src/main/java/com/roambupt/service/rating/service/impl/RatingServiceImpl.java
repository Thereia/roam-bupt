package com.roambupt.service.rating.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.api.model.dto.RatingCommand;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.RatingFeedVO;
import com.roambupt.api.model.vo.RatingReviewVO;
import com.roambupt.api.model.vo.RatingSummaryVO;
import com.roambupt.api.model.vo.RatingVO;
import com.roambupt.api.model.vo.UserBriefVO;
import com.roambupt.service.rating.repository.entity.TopicRatingDO;
import com.roambupt.service.rating.repository.mapper.TopicRatingMapper;
import com.roambupt.service.rating.service.RatingService;
import com.roambupt.service.topic.service.TopicService;
import com.roambupt.service.user.service.UserQueryService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 评分实现：一人一地点一行，重复提交即修改；均分用贝叶斯平滑现算。 */
@Service
public class RatingServiceImpl implements RatingService {

    /** 先验条数与先验均值：1 条 5 星不能排到 100 条 4.2 星前面。 */

    private static final int MAX_REVIEW_LENGTH = 200;
    private static final int MAX_IMAGES = 3;
    private static final int MAX_PAGE_SIZE = 50;
    private static final String IMAGE_PREFIX = "/files/";
    /** 与 topic_rating.review_images 的列宽一致。 */
    private static final int MAX_IMAGES_LENGTH = 1024;

    private final TopicRatingMapper ratingMapper;
    private final TopicService topicService;
    private final UserQueryService userQueryService;

    public RatingServiceImpl(TopicRatingMapper ratingMapper, TopicService topicService,
                             UserQueryService userQueryService) {
        this.ratingMapper = ratingMapper;
        this.topicService = topicService;
        this.userQueryService = userQueryService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RatingSummaryVO rate(long topicId, long uid, RatingCommand command) {
        int score = command.score();
        if (score < 1 || score > 5) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "评分只能是 1 到 5");
        }
        requirePlace(topicId);

        String reviewText = normalizeReview(command.review());
        String reviewImages = normalizeImages(command.images());
        LocalDateTime now = LocalDateTime.now();

        TopicRatingDO existing = ratingMapper.selectOne(Wrappers.<TopicRatingDO>lambdaQuery()
                .eq(TopicRatingDO::getUserId, uid)
                .eq(TopicRatingDO::getTopicId, topicId));
        if (existing != null) {
            ratingMapper.update(null, Wrappers.<TopicRatingDO>lambdaUpdate()
                    .eq(TopicRatingDO::getId, existing.getId())
                    .set(TopicRatingDO::getScore, score)
                    .set(TopicRatingDO::getReviewText, reviewText)
                    .set(TopicRatingDO::getReviewImages, reviewImages)
                    .set(TopicRatingDO::getUpdatedTime, now));
        } else {
            TopicRatingDO row = new TopicRatingDO();
            row.setUserId(uid);
            row.setTopicId(topicId);
            row.setScore(score);
            row.setReviewText(reviewText);
            row.setReviewImages(reviewImages);
            row.setCreatedTime(now);
            row.setUpdatedTime(now);
            try {
                ratingMapper.insert(row);
            } catch (DuplicateKeyException e) {
                // 并发首次提交：唯一键兜底，按修改处理
                ratingMapper.update(null, Wrappers.<TopicRatingDO>lambdaUpdate()
                        .eq(TopicRatingDO::getUserId, uid)
                        .eq(TopicRatingDO::getTopicId, topicId)
                        .set(TopicRatingDO::getScore, score)
                        .set(TopicRatingDO::getReviewText, reviewText)
                        .set(TopicRatingDO::getReviewImages, reviewImages)
                        .set(TopicRatingDO::getUpdatedTime, now));
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
        return new RatingSummaryVO(topicId, count, rawAverage(count, sum), myScore, distribution);
    }

    @Override
    public RatingFeedVO feed(long topicId, Long viewerUid, int page, int pageSize) {
        int pageNo = Math.max(1, page);
        int size = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        long total = ratingMapper.countByTopic(topicId);
        List<TopicRatingDO> rows = ratingMapper.selectList(Wrappers.<TopicRatingDO>lambdaQuery()
                .eq(TopicRatingDO::getTopicId, topicId)
                .orderByDesc(TopicRatingDO::getUpdatedTime)
                .orderByDesc(TopicRatingDO::getId)
                .last("LIMIT " + ((pageNo - 1) * size) + ", " + size));

        List<Long> authorIds = rows.stream()
                .map(TopicRatingDO::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, UserBriefVO> authors = new HashMap<>();
        for (UserBriefVO brief : userQueryService.briefs(authorIds)) {
            authors.put(brief.id(), brief);
        }
        List<RatingReviewVO> list = new ArrayList<>(rows.size());
        for (TopicRatingDO row : rows) {
            boolean mine = viewerUid != null && viewerUid.equals(row.getUserId());
            list.add(new RatingReviewVO(row.getId(), authors.get(row.getUserId()), row.getScore(),
                    row.getReviewText(), splitImages(row.getReviewImages()),
                    row.getCreatedTime(), row.getUpdatedTime(), mine));
        }
        return new RatingFeedVO(summary(topicId, viewerUid), PageResult.of(total, pageNo, size, list));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RatingSummaryVO clear(long topicId, long uid) {
        requirePlace(topicId);
        ratingMapper.delete(Wrappers.<TopicRatingDO>lambdaQuery()
                .eq(TopicRatingDO::getUserId, uid)
                .eq(TopicRatingDO::getTopicId, topicId));
        return summary(topicId, uid);
    }

    @Override
    public RatingVO ratingOf(long topicId) {
        long count = ratingMapper.countByTopic(topicId);
        long sum = ratingMapper.sumByTopic(topicId);
        double avg = rawAverage(count, sum);
        return new RatingVO(avg, Math.round(avg * 10) / 10.0, count);
    }

    @Override
    public Map<Long, RatingVO> ratingsOf(java.util.Collection<Long> topicIds) {
        Map<Long, RatingVO> result = new HashMap<>();
        if (topicIds == null || topicIds.isEmpty()) {
            return result;
        }
        for (TopicRatingMapper.TopicStatRow row : ratingMapper.statsByTopics(topicIds)) {
            double avg = rawAverage(row.getC(), row.getS());
            result.put(row.getTopicId(), new RatingVO(avg, Math.round(avg * 10) / 10.0, row.getC()));
        }
        return result;
    }

    private String normalizeReview(String review) {
        if (review == null) {
            return null;
        }
        String text = review.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.length() > MAX_REVIEW_LENGTH) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "评价最多 200 字");
        }
        return text;
    }

    private String normalizeImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        if (images.size() > MAX_IMAGES) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "最多上传 3 张图");
        }
        List<String> normalized = new ArrayList<>(images.size());
        for (String image : images) {
            String url = image == null ? "" : image.trim();
            if (!url.startsWith(IMAGE_PREFIX) || url.contains(",")) {
                throw new BusinessException(StatusEnum.PARAM_ERROR, "图片必须是站内上传的文件");
            }
            normalized.add(url);
        }
        String joined = String.join(",", normalized);
        if (joined.length() > MAX_IMAGES_LENGTH) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "图片地址过长");
        }
        return joined;
    }

    private List<String> splitImages(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        List<String> images = new ArrayList<>();
        for (String part : stored.split(",")) {
            if (!part.isBlank()) {
                images.add(part.trim());
            }
        }
        return images;
    }

    /** 展示用真实均分：没人打分就是 0，不再用 3.5 的先验分伪装。 */
    private double rawAverage(long count, long sum) {
        return count <= 0 ? 0d : (double) sum / count;
    }

    private void requirePlace(long topicId) {
        if (!topicService.isEnabledPlace(topicId)) {
            throw new BusinessException(StatusEnum.NOT_FOUND, "地点不存在");
        }
    }
}
