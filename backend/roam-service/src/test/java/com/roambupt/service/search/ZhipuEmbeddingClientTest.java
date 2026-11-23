package com.roambupt.service.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roambupt.service.search.config.SearchProperties;
import com.roambupt.service.search.embedding.EmbeddingException;
import com.roambupt.service.search.embedding.ZhipuEmbeddingClient;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ZhipuEmbeddingClientTest {

    private HttpServer server;
    private String endpoint;
    private volatile int status = 200;
    private volatile String body = "{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}";

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/embeddings", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/embeddings";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ZhipuEmbeddingClient client() {
        SearchProperties.Vector vector = new SearchProperties.Vector(
                true, endpoint, "test-key", "embedding-3", 3, 3000, 10);
        SearchProperties properties = new SearchProperties(
                true, "localhost", 9200, "idx", 1000, 2000, vector);
        return new ZhipuEmbeddingClient(properties, new ObjectMapper());
    }

    @Test
    void 解析返回的向量() {
        float[] vector = client().embed("教三自习");

        assertThat(vector).hasSize(3);
        assertThat(vector[0]).isEqualTo(0.1f);
    }

    @Test
    void 非200_抛异常由调用方降级() {
        status = 500;

        assertThatThrownBy(() -> client().embed("x")).isInstanceOf(EmbeddingException.class);
    }

    @Test
    void 返回为空_抛异常() {
        body = "{\"data\":[]}";

        assertThatThrownBy(() -> client().embed("x")).isInstanceOf(EmbeddingException.class);
    }
}
