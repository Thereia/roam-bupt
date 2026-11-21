package com.roambupt.service.space.plan;

import java.util.List;

/** 一条完整方案：按顺序的边 + 汇总值。 */
public record RoutePlan(List<RouteEdge> edges, int distanceMeters, int timeSeconds,
                        double maxCongestion) {
}
