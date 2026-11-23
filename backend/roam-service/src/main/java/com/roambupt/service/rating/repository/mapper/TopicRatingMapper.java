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

    @Select("<script>SELECT topic_id, COUNT(*) AS c, COALESCE(SUM(score),0) AS s FROM topic_rating "
            + "WHERE topic_id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "GROUP BY topic_id</script>")
    List<TopicStatRow> statsByTopics(@Param("ids") java.util.Collection<Long> ids);

    /** 批量统计行。 */
    class TopicStatRow {
        private Long topicId;
        private Long c;
        private Long s;

        public Long getTopicId() {
            return topicId;
        }

        public void setTopicId(Long topicId) {
            this.topicId = topicId;
        }

        public Long getC() {
            return c;
        }

        public void setC(Long c) {
            this.c = c;
        }

        public Long getS() {
            return s;
        }

        public void setS(Long s) {
            this.s = s;
        }
    }

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
