package com.roambupt.service.space.plan;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 寻路：纯函数，不读库、不读 Redis、不打日志。
 *
 * @param edgeFactors edgeId → 动态拥挤系数（缺失按 1.0）
 */
public interface RoutePlanner {

    Optional<RoutePlan> plan(RouteGraph graph, List<RouteStop> stops, TravelMode mode,
                             Map<Long, Double> edgeFactors);

    List<RoutePlan> alternatives(RouteGraph graph, List<RouteStop> stops, TravelMode mode,
                                 Map<Long, Double> edgeFactors, int limit);

    double weightOf(RouteEdge edge, TravelMode mode, Map<Long, Double> edgeFactors);
}
