package com.roambupt.service.rating.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 地点评分：一人一地点一行。 */
@Data
@TableName("topic_rating")
public class TopicRatingDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long topicId;
    private Integer score;
    private String reviewText;
    private String reviewImages;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
