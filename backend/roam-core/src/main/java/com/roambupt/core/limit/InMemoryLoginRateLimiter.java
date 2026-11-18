package com.roambupt.core.limit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.roambupt.core.config.AuthProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单机限流：按 1 分钟窗口计数。多实例时换成 Redis 实现，接口不变。
 */
@Component
public class InMemoryLoginRateLimiter implements LoginRateLimiter {

    private final Cache<String, AtomicInteger> counters;
    private final int limit;

    public InMemoryLoginRateLimiter(AuthProperties properties) {
        this.limit = properties.loginLimitPerMinute();
        this.counters = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(1))
                .maximumSize(100_000)
                .build();
    }

    @Override
    public boolean tryAcquire(String clientIp, String username) {
        String key = clientIp + ":" + (username == null ? "" : username);
        AtomicInteger counter = counters.get(key, k -> new AtomicInteger());
        return counter.incrementAndGet() <= limit;
    }
}
