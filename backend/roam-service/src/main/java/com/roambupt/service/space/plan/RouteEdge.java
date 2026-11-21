package com.roambupt.service.space.plan;

/**
 * 图里的一条边。congestionFactor 是静态系数（来自 route_edge 表），
 * 动态拥挤度由调用方按 edgeId 传进来，两者相乘。
 */
public record RouteEdge(long edgeId, long from, long to, int distanceMeters, int timeSeconds,
                        String edgeType, double congestionFactor) {
}
