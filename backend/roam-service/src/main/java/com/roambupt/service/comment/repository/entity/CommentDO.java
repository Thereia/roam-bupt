package com.roambupt.service.comment.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 评论表。只有 deleted 一个删除真相。 */
@Data
@TableName("comment")
public class CommentDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long postId;
    private Long userId;
    private String content;
    private Long parentId;
    private Long topId;
    private Integer replyCount;
    private Integer deleted;
    private LocalDateTime createdTime;
}
