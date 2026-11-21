package com.roambupt.service.space.repository.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/** 路网边。静态数据，只读。 */
@Data
@TableName("route_edge")
public class RouteEdgeDO {

    @TableId
    private Long edgeId;
    private Long fromNodeId;
    private Long toNodeId;
    private Integer distanceMeters;
    private Integer timeSeconds;
    private String edgeType;
    private Integer bikeAllowed;
    private BigDecimal congestionFactor;
    private Integer enabled;
}
