package com.roambupt.web.search;

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

/** 搜索 HTTP 接缝测试：ES 关闭，只验证 MySQL 兜底。 */
@SpringBootTest(properties = {
        "roam.auth.login-limit-per-minute=1000",
        "roam.jwt.secret=test-secret-must-be-long-enough-32bytes",
        "roam.post.auto-approve=true",
        "roam.search.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class SearchApiTest {

    private static final long PLACE_A = 113L;
    private static final long PLACE_B = 114L;

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

    private void createPost(String token, String title, String content, long placeId) throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "content", content,
                                "topics", List.of(Map.of("topicId", placeId)))))
                )
                .andExpect(status().isOk());
    }

    @Test
    void 搜索_关闭ES_按标题命中() throws Exception {
        String token = register("s_user1");
        createPost(token, "教三自习位观察zzq1", "四楼靠窗位置很紧张", PLACE_A);
        createPost(token, "食堂新窗口", "砂锅不错", PLACE_B);

        mockMvc.perform(get("/api/v1/search").param("q", "zzq1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].title").value("教三自习位观察zzq1"));
    }

    @Test
    void 搜索_关键词为空_返回4001() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void 搜索_带地点过滤_只返回该地点帖子() throws Exception {
        String token = register("s_user2");
        createPost(token, "教三观察zzq2", "同一个词", PLACE_A);
        createPost(token, "食堂观察zzq2", "同一个词", PLACE_B);

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "zzq2")
                        .param("topicId", String.valueOf(PLACE_A)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].title").value("教三观察zzq2"));
    }

    @Test
    void 搜索_无结果_返回空列表而不是错误() throws Exception {
        mockMvc.perform(get("/api/v1/search").param("q", "不存在的关键词xyz"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.list").isEmpty());
    }
}
