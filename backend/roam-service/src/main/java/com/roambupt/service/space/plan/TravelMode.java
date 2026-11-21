package com.roambupt.service.space.plan;

/** 寻路权重。DISTANCE 只看距离；TIME / MIXED 看时间并计入拥挤度。 */
public enum TravelMode {
    DISTANCE,
    TIME,
    MIXED
}
