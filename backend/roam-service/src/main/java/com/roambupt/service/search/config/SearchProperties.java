package com.roambupt.service.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 搜索配置。ES 关闭或不可用时走 MySQL 兜底。
 *
 * <p>vector 段控制语义召回：开启后索引额外写入 embedding，检索时 BM25 与 kNN 两路 RRF 融合。</p>
 */
@ConfigurationProperties(prefix = "roam.search")
public record SearchProperties(boolean enabled, String host, int port, String index,
                               int connectTimeoutMs, int socketTimeoutMs, Vector vector) {

    /** 向量召回配置。 */
    public record Vector(boolean enabled, String endpoint, String apiKey, String model,
                         int dims, int timeoutMs, int topK) {
    }
}
