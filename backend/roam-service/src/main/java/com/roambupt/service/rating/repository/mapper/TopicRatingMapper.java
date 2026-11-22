package com.roambupt.service.rating.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.roambupt.service.rating.repository.entity.TopicRatingDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 评分 Mapper。 */
@Mapper
public interface TopicRatingMapper extends BaseMapper<TopicRatingDO> {

    @Select("SELECT COUNT(*) FROM topic_rating WHERE topic_id = #{topicId}")
    long countByTopic(@Param("topicId") long topicId);

    @Select("SELECT COALESCE(SUM(score), 0) FROM topic_rating WHERE topic_id = #{topicId}")
    long sumByTopic(@Param("topicId") long topicId);

    @Select("SELECT score, COUNT(*) AS c FROM topic_rating WHERE topic_id = #{topicId} GROUP BY score")
    List<ScoreRow> distribution(@Param("topicId") long topicId);

    /** 分布行。 */
    class ScoreRow {
        private Integer score;
        private Long c;

        public Integer getScore() {
            return score;
        }

        public void setScore(Integer score) {
            this.score = score;
        }

        public Long getC() {
            return c;
        }

        public void setC(Long c) {
            this.c = c;
        }
    }
}
