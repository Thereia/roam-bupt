package com.roambupt.service.user.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 关注关系。唯一键 (user_id, target_user_id) 保证幂等。 */
@Data
@TableName("follow")
public class FollowDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long targetUserId;
    private LocalDateTime createdTime;
}
