package com.roambupt.service.post.repository.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 帖子-主题关联。sort_no 只对地点主题有意义。 */
@Data
@TableName("post_topic")
public class PostTopicDO {

    private Long postId;
    private Long topicId;
    private Integer sortNo;
}
