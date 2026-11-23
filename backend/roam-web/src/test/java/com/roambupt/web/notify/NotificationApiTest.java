package com.roambupt.web.notify;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 站内通知 HTTP 接缝测试。 */
@SpringBootTest(properties = {
        "roam.auth.login-limit-per-minute=1000",
        "roam.jwt.secret=test-secret-must-be-long-enough-32bytes",
        "roam.post.auto-approve=true",
        "roam.search.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class NotificationApiTest {

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
                "title", "通知测试帖",
                "content", "用来触发通知",
                "topics", List.of(Map.of("topicId", PLACE_ID))));
        String response = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private void comment(String token, long postId, String content) throws Exception {
        mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", content))))
                .andExpect(status().isOk());
    }

    @Test
    void 评论后_作者收到一条通知() throws Exception {
        String author = register("n_author1");
        String other = register("n_other1");
        long postId = createPost(author);
        comment(other, postId, "来评论了");

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].type").value("COMMENT"))
                .andExpect(jsonPath("$.data.list[0].actor.id").isNumber())
                .andExpect(jsonPath("$.data.list[0].postTitle").isNotEmpty());
    }

    @Test
    void 自己评论自己_不产生通知() throws Exception {
        String author = register("n_author2");
        long postId = createPost(author);
        comment(author, postId, "自己评论");

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void 点赞后_作者收到LIKE通知且重复点赞只留一条() throws Exception {
        String author = register("n_author3");
        String other = register("n_other3");
        long postId = createPost(author);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/posts/" + postId + "/like")
                            .header("Authorization", "Bearer " + other))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].type").value("LIKE"));
    }

    @Test
    void 回复评论_被回复者收到REPLY通知() throws Exception {
        String author = register("n_author4");
        String first = register("n_first4");
        String second = register("n_second4");
        long postId = createPost(author);

        String response = mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + first)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "一级评论"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long topId = objectMapper.readTree(response).at("/data/id").asLong();

        mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + second)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "回复", "parentId", topId))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].type").value("REPLY"));
    }

    @Test
    void 未读数与标记已读() throws Exception {
        String author = register("n_author5");
        String other = register("n_other5");
        long postId = createPost(author);
        comment(other, postId, "一");
        comment(other, postId, "二");

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2));

        mockMvc.perform(put("/api/v1/notifications/read-all")
                        .header("Authorization", "Bearer " + author))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                        .header("Authorization", "Bearer " + author))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));
    }
}
