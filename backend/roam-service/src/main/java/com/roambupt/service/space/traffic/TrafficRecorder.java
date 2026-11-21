package com.roambupt.service.space.traffic;

import java.util.Collection;
import java.util.Map;

/** 拥挤度记录：寻路成功就记一次，读取时现算。 */
public interface TrafficRecorder {

    void record(Collection<Long> edgeIds);

    Map<Long, Double> factors(Collection<Long> edgeIds);
}
