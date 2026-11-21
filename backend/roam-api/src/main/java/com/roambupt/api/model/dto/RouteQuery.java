package com.roambupt.api.model.dto;

import java.util.List;

/** 寻路入参。mode: DISTANCE / TIME / MIXED，缺省 MIXED。 */
public record RouteQuery(List<RouteStopRequest> stops, String mode) {
}
