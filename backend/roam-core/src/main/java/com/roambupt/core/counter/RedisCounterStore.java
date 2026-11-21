package com.roambupt.core.counter;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Redis 计数器：INCR 后补一次 EXPIRE，过期即归零。 */
@Component
public class RedisCounterStore implements CounterStore {

    private final StringRedisTemplate redis;

    public RedisCounterStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, Duration ttl) {
        Long value = redis.opsForValue().increment(key);
        redis.expire(key, ttl);
        return value == null ? 0 : value;
    }

    @Override
    public long get(String key) {
        String value = redis.opsForValue().get(key);
        return value == null ? 0 : Long.parseLong(value);
    }

    @Override
    public java.util.Map<String, Long> multiGet(java.util.List<String> keys) {
        java.util.Map<String, Long> result = new java.util.HashMap<>();
        if (keys == null || keys.isEmpty()) {
            return result;
        }
        java.util.List<String> values = redis.opsForValue().multiGet(keys);
        if (values == null) {
            return result;
        }
        for (int i = 0; i < keys.size(); i++) {
            String value = values.get(i);
            if (value != null) {
                result.put(keys.get(i), Long.parseLong(value));
            }
        }
        return result;
    }
}
