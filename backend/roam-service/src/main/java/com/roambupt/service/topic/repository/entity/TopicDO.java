package com.roambupt.service.topic.repository.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 主题表：地点（PLACE）与分类（CATEGORY）。 */
@Data
@TableName("topic")
public class TopicDO {

    @TableId(type = IdType.INPUT)
    private Long id;
    private String type;
    private String name;
    private String category;
    private String sourceCategory;
    private Long nodeId;
    private String sourceRef;
    private String cover;
    private String description;
    private Integer heat;
    private Integer enabled;
}
