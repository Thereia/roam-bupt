package com.roambupt.service.moderation.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 敏感词。 */
@Data
@TableName("sensitive_word")
public class SensitiveWordDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String word;
    private Integer enabled;
    private LocalDateTime createdTime;
}
