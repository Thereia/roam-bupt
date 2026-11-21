package com.roambupt.service.space.traffic;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 拥挤度：纯公式 + 记录器（用内存计数器替身，不依赖 Redis）。 */
class TrafficRecorderTest {

    @Test
    void 没有计数_系数为1() {
        assertThat(CongestionModel.factor(0, 0, 1.0)).isEqualTo(1.0);
    }

    @Test
    void 计数越多_系数越大且单调() {
        double none = CongestionModel.factor(1, 10, 1.0);
        double busy = CongestionModel.factor(5, 10, 1.0);
        double busiest = CongestionModel.factor(10, 10, 1.0);

        assertThat(none).isGreaterThan(1.0);
        assertThat(busy).isGreaterThan(none);
        assertThat(busiest).isGreaterThan(busy);
        assertThat(busiest).isLessThan(20.0);
    }

    @Test
    void 记录之后_系数上升且未记录的边不受影响() {
        InMemoryCounterStore store = new InMemoryCounterStore();
        TrafficRecorder recorder = new TrafficRecorderImpl(store, 1.0, 5);

        recorder.record(List.of(10L, 10L, 10L));
        recorder.record(List.of(11L));

        Map<Long, Double> factors = recorder.factors(List.of(10L, 11L));

        assertThat(factors.get(10L)).isGreaterThan(factors.get(11L));
        assertThat(factors.get(11L)).isGreaterThanOrEqualTo(1.0);
    }

    /** 计数器替身：放在外部依赖的接缝上，不是被测对象本身。 */
    static class InMemoryCounterStore implements com.roambupt.core.counter.CounterStore {

        private final Map<String, Long> values = new java.util.HashMap<>();

        @Override
        public long increment(String key, java.time.Duration ttl) {
            return values.merge(key, 1L, Long::sum);
        }

        @Override
        public long get(String key) {
            return values.getOrDefault(key, 0L);
        }
    }
}
