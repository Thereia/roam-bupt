package com.roambupt.service.notify.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 通知投递失败暂存（发 MQ 失败或发布确认 nack 时落库，定时重投）。 */
@Data
@TableName("notify_outbox")
public class NotifyOutboxDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String payload;
    private String status;
    private Integer attempts;
    private LocalDateTime nextRetryTime;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}
