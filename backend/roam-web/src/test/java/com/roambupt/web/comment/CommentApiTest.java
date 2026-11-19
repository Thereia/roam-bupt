package com.roambupt.web.comment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roambupt.core.session.SessionStore;
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

/** 评论 HTTP 接缝测试。 */
@SpringBootTest(properties = {
        "roam.auth.login-limit-per-minute=1000",
        "roam.jwt.secret=test-secret-must-be-long-enough-32bytes",
        "roam.post.auto-approve=true"
})
@AutoConfigureMockMvc
@Transactional
class CommentApiTest {

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

    @Autowired
    private SessionStore sessionStore;

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
                "title", "评论测试帖",
                "content", "用来挂评论的帖子",
                "topics", List.of(Map.of("topicId", PLACE_ID))));
        String response = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private long comment(String token, long postId, String content, Long parentId) throws Exception {
        Map<String, Object> payload = parentId == null
                ? Map.of("content", content)
                : Map.of("content", content, "parentId", parentId);
        String response = mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    @Test
    void 评论_登录后发表_返回id() throws Exception {
        String token = register("c_user1");
        long postId = createPost(token);

        mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "第一条评论"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber());
    }

    @Test
    void 评论_未登录_返回401() throws Exception {
        String token = register("c_user2");
        long postId = createPost(token);

        mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "游客评论"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 评论_帖子不存在_返回404() throws Exception {
        String token = register("c_user3");
        mockMvc.perform(post("/api/v1/posts/999999/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "不存在")))
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void 评论_超长_返回4001() throws Exception {
        String token = register("c_user4");
        long postId = createPost(token);
        mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "a".repeat(1001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void 评论列表_匿名可见且回复计数正确() throws Exception {
        String token = register("c_user5");
        long postId = createPost(token);
        long topId = comment(token, postId, "一级评论", null);
        comment(token, postId, "回复一", topId);

        mockMvc.perform(get("/api/v1/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].replyCount").value(1));

        mockMvc.perform(get("/api/v1/comments/" + topId + "/replies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].content").value("回复一"));
    }

    @Test
    void 回复_父评论不存在_返回4001() throws Exception {
        String token = register("c_user6");
        long postId = createPost(token);
        mockMvc.perform(post("/api/v1/posts/" + postId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "回复", "parentId", 999999))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void 删除_他人评论_返回403() throws Exception {
        String author = register("c_author");
        String other = register("c_other");
        long postId = createPost(author);
        long commentId = comment(author, postId, "我的评论", null);

        mockMvc.perform(delete("/api/v1/comments/" + commentId)
                        .header("Authorization", "Bearer " + other))
                .andExpect(status().isForbidden());
    }

    @Test
    void 帖子详情_评论数等于评论总数() throws Exception {
        String token = register("c_user7");
        long postId = createPost(token);
        long topId = comment(token, postId, "一级", null);
        comment(token, postId, "回复", topId);

        mockMvc.perform(get("/api/v1/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.comment").value(2));
    }
}
