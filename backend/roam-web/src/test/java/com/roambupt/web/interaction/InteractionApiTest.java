package com.roambupt.web.interaction;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 点赞/收藏 HTTP 接缝测试。 */
@SpringBootTest(properties = {
        "roam.auth.login-limit-per-minute=1000",
        "roam.jwt.secret=test-secret-must-be-long-enough-32bytes",
        "roam.post.auto-approve=true"
})
@AutoConfigureMockMvc
@Transactional
class InteractionApiTest {

    private static final long PLACE_ID = 113L;

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

    private long createPost(String token) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "互动测试帖",
                "content", "用来点赞的帖子",
                "topics", List.of(Map.of("topicId", PLACE_ID))));
        String response = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    @Test
    void 点赞_成功_返回active和计数() throws Exception {
        String token = register("i_user1");
        long postId = createPost(token);

        mockMvc.perform(post("/api/v1/posts/" + postId + "/like")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.count").value(1));
    }

    @Test
    void 点赞_重复_返回200且计数不变() throws Exception {
        String token = register("i_user2");
        long postId = createPost(token);

        mockMvc.perform(post("/api/v1/posts/" + postId + "/like")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/posts/" + postId + "/like")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));
    }

    @Test
    void 取消点赞_未点过_返回200() throws Exception {
        String token = register("i_user3");
        long postId = createPost(token);

        mockMvc.perform(delete("/api/v1/posts/" + postId + "/like")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    void 点赞_帖子不存在_返回404() throws Exception {
        String token = register("i_user4");
        mockMvc.perform(post("/api/v1/posts/999999/like")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void 点赞_未登录_返回401() throws Exception {
        mockMvc.perform(post("/api/v1/posts/1/like"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 详情_登录用户_myLiked为true() throws Exception {
        String token = register("i_user5");
        long postId = createPost(token);
        mockMvc.perform(post("/api/v1/posts/" + postId + "/like")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/posts/" + postId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myLiked").value(true))
                .andExpect(jsonPath("$.data.counts.like").value(1));
    }

    @Test
    void 详情_匿名_myLiked为null() throws Exception {
        String token = register("i_user6");
        long postId = createPost(token);

        mockMvc.perform(get("/api/v1/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myLiked").doesNotExist())
                .andExpect(jsonPath("$.data.counts.like").value(0));
    }
}
