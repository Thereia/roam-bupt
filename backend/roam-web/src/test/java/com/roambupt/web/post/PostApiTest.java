package com.roambupt.web.post;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 发帖与地点页 HTTP 接缝测试。
 */
@SpringBootTest(properties = {
        "roam.auth.login-limit-per-minute=1000",
        "roam.jwt.secret=test-secret-must-be-long-enough-32bytes",
        "roam.post.auto-approve=true",
        "roam.search.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class PostApiTest {

    private static final long PLACE_CANTEEN = 113L;
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

    @Autowired
    private SessionStore sessionStore;

    private String registerAndGetToken(String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "secret123", "nickname", "发帖人"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/token").asText();
    }

    private String createBody(String title, List<Map<String, Object>> topics) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "title", title,
                "content", "四楼东侧靠窗一共 32 个座位，晚上七点以后基本没位子。",
                "topics", topics));
    }

    @Test
    void 发帖_成功_详情匿名可见且带地点顺序() throws Exception {
        String token = registerAndGetToken("poster_ok");
        List<Map<String, Object>> topics = List.of(
                Map.of("topicId", PLACE_CANTEEN, "sortNo", 0),
                Map.of("topicId", CATEGORY_SELF_STUDY, "sortNo", 0));

        String response = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("教三自习位记录", topics)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.status").value("ONLINE"))
                .andReturn().getResponse().getContentAsString();

        long postId = objectMapper.readTree(response).at("/data/id").asLong();

        mockMvc.perform(get("/api/v1/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("教三自习位记录"))
                .andExpect(jsonPath("$.data.author.nickname").value("发帖人"))
                .andExpect(jsonPath("$.data.topics[0].name").value("学生食堂"))
                .andExpect(jsonPath("$.data.content").isNotEmpty());
    }

    @Test
    void 发帖_只挂分类_返回4001() throws Exception {
        String token = registerAndGetToken("poster_cat");
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("只有分类", List.of(Map.of("topicId", CATEGORY_SELF_STUDY)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void 路径参数不是数字_返回400而不是500() throws Exception {
        mockMvc.perform(get("/api/v1/posts/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void 详情每次访问_浏览量加一() throws Exception {
        String token = registerAndGetToken("p_view1");
        String created = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("浏览量测试帖", List.of(Map.of("topicId", PLACE_CANTEEN)))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long postId = objectMapper.readTree(created).at("/data/id").asLong();
        mockMvc.perform(get("/api/v1/posts/" + postId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.counts.view").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void 发帖_未登录_返回401() throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("游客发帖", List.of(Map.of("topicId", PLACE_CANTEEN)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 发帖_主题重复_返回4001() throws Exception {
        String token = registerAndGetToken("poster_dup");
        List<Map<String, Object>> topics = List.of(
                Map.of("topicId", PLACE_CANTEEN),
                Map.of("topicId", PLACE_CANTEEN));
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("重复主题", topics)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void 帖子不存在_匿名访问_返回404() throws Exception {
        mockMvc.perform(get("/api/v1/posts/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void 地点页_返回帖子数与内容流() throws Exception {
        String token = registerAndGetToken("poster_topic");
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("食堂新窗口测评", List.of(Map.of("topicId", PLACE_CANTEEN)))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/topics/" + PLACE_CANTEEN + "/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.list[0].title").isNotEmpty());

        mockMvc.perform(get("/api/v1/topics/" + PLACE_CANTEEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("学生食堂"))
                .andExpect(jsonPath("$.data.postCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void 首页信息流_游客可访问() throws Exception {
        mockMvc.perform(get("/api/v1/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1));
    }

    @Test
    void 发帖_正文超过48KB_返回4001() throws Exception {
        String token = registerAndGetToken("poster_big");
        String big = "a".repeat(50 * 1024);
        String body = objectMapper.writeValueAsString(Map.of(
                "title", "太长", "content", big,
                "topics", List.of(Map.of("topicId", PLACE_CANTEEN))));
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }
}
