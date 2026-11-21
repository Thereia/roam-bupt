package com.roambupt.service.space.repository.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 路网节点。静态数据，只读。 */
@Data
@TableName("route_node")
public class RouteNodeDO {

    @TableId
    private Long nodeId;
    private String name;
    private String nodeType;
    private Long buildingId;
    private Integer floor;
    private BigDecimal xRatio;
    private BigDecimal yRatio;
    private String poiCategory;
    private Integer enabled;
}
