package com.roambupt.service.search.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roambupt.service.search.config.SearchProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 智谱 embedding-3 客户端（OpenAI 兼容 /embeddings）。
 *
 * <p>只在 roam.search.vector.enabled=true 时注册；失败抛 {@link EmbeddingException}，
 * 检索侧降级为纯 BM25。</p>
 */
@Component
@ConditionalOnProperty(name = "roam.search.vector.enabled", havingValue = "true")
public class ZhipuEmbeddingClient implements EmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(ZhipuEmbeddingClient.class);

    private final SearchProperties.Vector vector;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ZhipuEmbeddingClient(SearchProperties properties, ObjectMapper objectMapper) {
        this.vector = properties.vector();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500, vector.timeoutMs())))
                .build();
    }

    @Override
    public float[] embed(String text) {
        if (vector.apiKey() == null || vector.apiKey().isBlank()) {
            throw new EmbeddingException("embedding api key 未配置");
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", vector.model(),
                    "input", List.of(text),
                    "dimensions", vector.dims()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(vector.endpoint()))
                    .timeout(Duration.ofMillis(vector.timeoutMs()))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + vector.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new EmbeddingException("embedding http " + response.statusCode());
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode values = root.at("/data/0/embedding");
            if (!values.isArray() || values.isEmpty()) {
                throw new EmbeddingException("embedding 返回为空");
            }
            float[] result = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                result[i] = (float) values.get(i).asDouble();
            }
            return result;
        } catch (EmbeddingException e) {
            throw e;
        } catch (Exception e) {
            log.warn("embedding.failed reason={}", e.getMessage());
            throw new EmbeddingException("embedding 调用失败", e);
        }
    }
}
