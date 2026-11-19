package com.roambupt.service.search.config;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** ES 低频客户端：只有开启搜索时才创建。 */
@Configuration
@EnableConfigurationProperties(SearchProperties.class)
public class SearchConfig {

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "roam.search", name = "enabled", havingValue = "true")
    public RestClient searchRestClient(SearchProperties properties) {
        return RestClient.builder(new HttpHost(properties.host(), properties.port(), "http"))
                .setRequestConfigCallback(builder -> builder
                        .setConnectTimeout(properties.connectTimeoutMs())
                        .setSocketTimeout(properties.socketTimeoutMs()))
                .build();
    }
}
