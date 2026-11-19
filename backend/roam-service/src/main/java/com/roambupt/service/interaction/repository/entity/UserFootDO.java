package com.roambupt.service.interaction.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 用户足迹：点赞/收藏。唯一键 (user_id, post_id, action) 保证幂等。 */
@Data
@TableName("user_foot")
public class UserFootDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long postId;
    private String action;
    private LocalDateTime createdTime;
}
