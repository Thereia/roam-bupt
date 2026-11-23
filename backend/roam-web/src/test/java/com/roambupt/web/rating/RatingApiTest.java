package com.roambupt.web.rating;

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

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 地点评分：贝叶斯均分与改分。 */
@SpringBootTest(properties = {
        "roam.auth.login-limit-per-minute=1000",
        "roam.jwt.secret=test-secret-must-be-long-enough-32bytes",
        "roam.post.auto-approve=true",
        "roam.search.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class RatingApiTest {

    private static final long PLACE = 79L;      // 教三楼
    private static final long CATEGORY = 1002L; // 分类

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
    void 匿名看评分_零条时返回先验均分() throws Exception {
        mockMvc.perform(get("/api/v1/topics/" + PLACE + "/ratings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.average").value(3.5))
                .andExpect(jsonPath("$.data.myScore").doesNotExist());
    }

    @Test
    void 打分_首次_均分按贝叶斯平滑() throws Exception {
        String token = register("rt_user1");
        mockMvc.perform(put("/api/v1/topics/" + PLACE + "/ratings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("score", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.myScore").value(5))
                // (10*3.5 + 5) / 11 = 3.636…
                .andExpect(jsonPath("$.data.average").value(org.hamcrest.Matchers.closeTo(3.636, 0.01)));
    }

    @Test
    void 改分_行数不变且均分更新() throws Exception {
        String token = register("rt_user2");
        mockMvc.perform(put("/api/v1/topics/" + PLACE + "/ratings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("score", 5))))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/topics/" + PLACE + "/ratings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("score", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.myScore").value(1))
                // (35 + 1) / 11 = 3.272…
                .andExpect(jsonPath("$.data.average").value(org.hamcrest.Matchers.closeTo(3.272, 0.01)));
    }

    @Test
    void 打分类_返回404() throws Exception {
        String token = register("rt_user3");
        mockMvc.perform(put("/api/v1/topics/" + CATEGORY + "/ratings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("score", 4))))
                .andExpect(status().isNotFound());
    }

    @Test
    void 分数越界_返回4001() throws Exception {
        String token = register("rt_user4");
        mockMvc.perform(put("/api/v1/topics/" + PLACE + "/ratings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("score", 6))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void 未登录打分_返回401() throws Exception {
        mockMvc.perform(put("/api/v1/topics/" + PLACE + "/ratings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("score", 4))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 撤回评分_计数归零() throws Exception {
        String token = register("rt_user7");
        mockMvc.perform(put("/api/v1/topics/" + PLACE + "/ratings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("score", 5))))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/topics/" + PLACE + "/ratings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.myScore").doesNotExist());
    }

    @Test
    void 撤回评分_未登录_返回401() throws Exception {
        mockMvc.perform(delete("/api/v1/topics/" + PLACE + "/ratings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 详情页_带出评分() throws Exception {
        String token = register("rt_user5");
        mockMvc.perform(put("/api/v1/topics/" + PLACE + "/ratings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("score", 5))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/topics/" + PLACE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rating.count").value(1))
                .andExpect(jsonPath("$.data.rating.displayAvg").value(org.hamcrest.Matchers.greaterThan(3.5)));
    }
}
