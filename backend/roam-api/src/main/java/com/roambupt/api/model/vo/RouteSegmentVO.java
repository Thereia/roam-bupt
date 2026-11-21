package com.roambupt.api.model.vo;

/** 路线的一段：相邻两个节点之间的一条边。 */
public record RouteSegmentVO(String fromName, String toName, int distanceMeters,
                             int timeSeconds, double congestion, String edgeType) {
}
