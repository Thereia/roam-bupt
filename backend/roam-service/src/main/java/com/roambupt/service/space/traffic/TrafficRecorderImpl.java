package com.roambupt.service.space.traffic;

import com.roambupt.core.counter.CounterStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 按分钟桶累加，读时归一化。Redis 不可用不抛异常，按无拥挤处理。 */
@Component
public class TrafficRecorderImpl implements TrafficRecorder {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final CounterStore counterStore;
    private final double k;
    private final int windowMinutes;

    public TrafficRecorderImpl(CounterStore counterStore,
                               @Value("${roam.space.congestion-k:1.0}") double k,
                               @Value("${roam.space.congestion-window-minutes:5}") int windowMinutes) {
        this.counterStore = counterStore;
        this.k = k;
        this.windowMinutes = windowMinutes;
    }

    @Override
    public void record(Collection<Long> edgeIds) {
        if (edgeIds == null || edgeIds.isEmpty()) {
            return;
        }
        String minute = LocalDateTime.now().format(MINUTE);
        Duration ttl = Duration.ofMinutes(windowMinutes + 1L);
        try {
            for (Long edgeId : edgeIds) {
                counterStore.increment(key(edgeId, minute), ttl);
            }
        } catch (Exception ignored) {
            // 拥挤度是锦上添花，Redis 挂了不影响寻路
        }
    }

    @Override
    public Map<Long, Double> factors(Collection<Long> edgeIds) {
        Map<Long, Double> factors = new HashMap<>();
        if (edgeIds == null || edgeIds.isEmpty()) {
            return factors;
        }
        String minute = LocalDateTime.now().format(MINUTE);
        List<String> keys = edgeIds.stream().map(edgeId -> key(edgeId, minute)).toList();
        Map<String, Long> counts;
        try {
            counts = counterStore.multiGet(keys);
        } catch (Exception ignored) {
            return factors;
        }
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        for (Long edgeId : edgeIds) {
            long count = counts.getOrDefault(key(edgeId, minute), 0L);
            factors.put(edgeId, CongestionModel.factor(count, max, k));
        }
        return factors;
    }

    private String key(long edgeId, String minute) {
        return "roam:traffic:" + edgeId + ":" + minute;
    }
}
