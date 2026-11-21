package com.roambupt.api.model.vo;

import java.util.List;

/** 一条完整方案。 */
public record RoutePlanVO(List<RouteSegmentVO> segments, int totalDistanceMeters,
                          int totalTimeSeconds, List<Long> nodeIds, double maxCongestion) {
}
