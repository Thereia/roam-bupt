package com.roambupt.core.counter;

import java.time.Duration;

/**
 * 带 TTL 的计数器。接口在 core，Redis 实现在 core，测试替身放 test。
 */
public interface CounterStore {

    long increment(String key, Duration ttl);

    long get(String key);

    /** 批量读，缺失的 key 不返回。 */
    java.util.Map<String, Long> multiGet(java.util.List<String> keys);
}
