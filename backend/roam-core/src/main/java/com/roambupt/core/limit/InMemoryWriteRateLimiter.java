package com.roambupt.core.limit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.roambupt.core.config.SecurityProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** 单机写操作限流：1 分钟滑动窗口，按 uid 或 IP 计数。多实例时换 Redis 实现。 */
@Component
public class InMemoryWriteRateLimiter implements WriteRateLimiter {

    private final Cache<String, AtomicInteger> counters;
    private final int limit;

    public InMemoryWriteRateLimiter(SecurityProperties properties) {
        this.limit = properties.writePerMinute();
        this.counters = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .maximumSize(200_000)
                .build();
    }

    @Override
    public boolean tryAcquire(String key) {
        AtomicInteger counter = counters.get(key, k -> new AtomicInteger());
        return counter.incrementAndGet() <= limit;
    }
}
