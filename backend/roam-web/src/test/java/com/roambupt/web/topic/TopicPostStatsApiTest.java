package com.roambupt.web.topic;

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

/** 侧栏「逛什么」的帖子数统计。 */
@SpringBootTest(properties = {
        "roam.auth.login-limit-per-minute=1000",
        "roam.jwt.secret=test-secret-must-be-long-enough-32bytes",
        "roam.post.auto-approve=true",
        "roam.search.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class TopicPostStatsApiTest {

    private static final long CATEGORY_SELF_STUDY = 1002L;

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

    @Test
    void 统计_总数与分类帖数_跟着新帖增长() throws Exception {
        String token = register("stats_user");
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "只挂自习的统计帖",
                                "content", "正文",
                                "topics", List.of(Map.of("topicId", CATEGORY_SELF_STUDY))))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/topics/stats/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.categories[0].category").value("自习"))
                .andExpect(jsonPath("$.data.categories[0].count").value(1));
    }
}
