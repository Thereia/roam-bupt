package com.roambupt.web.limit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roambupt.web.auth.FakeSessionStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 写操作限流：把阈值调到 3，第 4 次写就该 429。 */
@SpringBootTest(properties = {
        "roam.auth.login-limit-per-minute=1000",
        "roam.jwt.secret=test-secret-must-be-long-enough-32bytes",
        "roam.post.auto-approve=true",
        "roam.search.enabled=false",
        "roam.security.write-per-minute=3"
})
@AutoConfigureMockMvc
@Transactional
class RateLimitApiTest {

    private static final long PLACE = 79L;

    @TestConfiguration
    static class FakeStoreConfig {
        @Bean
        @Primary
        FakeSessionStore fakeSessionStore() {
            return new FakeSessionStore();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "secret123", "nickname", username))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/token").asText();
    }

    private String postBody(int index) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "title", "限流测试 " + index,
                "content", "正文",
                "topics", List.of(Map.of("topicId", PLACE))));
    }

    @Test
    void 写操作超过阈值_返回429() throws Exception {
        String token = register("rl_user1");
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/posts")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(postBody(i)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody(4)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429));
    }

    @Test
    void 读操作不受写限流影响() throws Exception {
        String token = register("rl_user2");
        // 自己造一条，别依赖本地库里的种子 id
        String created = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(postBody(9)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long postId = objectMapper.readTree(created).at("/data/id").asLong();
        for (int i = 0; i < 8; i++) {
            mockMvc.perform(get("/api/v1/posts").param("pageSize", "1"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        // token 只是为了证明登录态不影响读
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
