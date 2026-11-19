package com.roambupt.service.notify.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 站内通知。 */
@Data
@TableName("notification")
public class NotificationDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long actorId;
    private String type;
    private String sourceType;
    private Long sourceId;
    private Long postId;
    private String content;
    private Integer readFlag;
    private LocalDateTime createdTime;
}
