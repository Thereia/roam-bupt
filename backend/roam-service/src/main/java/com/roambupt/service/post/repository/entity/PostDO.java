package com.roambupt.service.post.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 帖子表。 */
@Data
@TableName("post")
public class PostDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String status;
    private Integer heat;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
