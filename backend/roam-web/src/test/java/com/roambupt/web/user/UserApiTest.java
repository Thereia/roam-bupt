package com.roambupt.web.user;

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

/** 个人主页与关注的 HTTP 接缝测试。 */
@SpringBootTest(properties = {
        "roam.auth.login-limit-per-minute=1000",
        "roam.jwt.secret=test-secret-must-be-long-enough-32bytes",
        "roam.post.auto-approve=true",
        "roam.search.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class UserApiTest {

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

    private record Account(String token, long id) {
    }

    private Account register(String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "secret123", "nickname", username))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var node = objectMapper.readTree(response).at("/data");
        return new Account(node.at("/token").asText(), node.at("/user/id").asLong());
    }

    private void createPost(Account account, String title) throws Exception {
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + account.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title,
                                "content", "正文",
                                "topics", List.of(Map.of("topicId", PLACE_ID)))))
                )
                .andExpect(status().isOk());
    }

    @Test
    void 主页_匿名_返回计数且isFollowed为空() throws Exception {
        Account a = register("up_a1");
        createPost(a, "帖子一");
        createPost(a, "帖子二");

        mockMvc.perform(get("/api/v1/users/" + a.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.postCount").value(2))
                .andExpect(jsonPath("$.data.followerCount").value(0))
                .andExpect(jsonPath("$.data.isFollowed").doesNotExist());
    }

    @Test
    void 关注_成功_粉丝数加一且幂等() throws Exception {
        Account target = register("up_t2");
        Account fan = register("up_f2");

        mockMvc.perform(post("/api/v1/users/" + target.id() + "/follow")
                        .header("Authorization", "Bearer " + fan.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followed").value(true))
                .andExpect(jsonPath("$.data.followerCount").value(1));

        mockMvc.perform(post("/api/v1/users/" + target.id() + "/follow")
                        .header("Authorization", "Bearer " + fan.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followerCount").value(1));

        mockMvc.perform(get("/api/v1/users/" + target.id())
                        .header("Authorization", "Bearer " + fan.token()))
                .andExpect(jsonPath("$.data.isFollowed").value(true));
    }

    @Test
    void 关注_自己_返回4001() throws Exception {
        Account a = register("up_self");
        mockMvc.perform(post("/api/v1/users/" + a.id() + "/follow")
                        .header("Authorization", "Bearer " + a.token()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void 关注_目标不存在_返回404() throws Exception {
        Account a = register("up_nf");
        mockMvc.perform(post("/api/v1/users/999999/follow")
                        .header("Authorization", "Bearer " + a.token()))
                .andExpect(status().isNotFound());
    }

    @Test
    void 取关_未关注_返回200() throws Exception {
        Account target = register("up_t5");
        Account fan = register("up_f5");
        mockMvc.perform(delete("/api/v1/users/" + target.id() + "/follow")
                        .header("Authorization", "Bearer " + fan.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followed").value(false));
    }

    @Test
    void 关注流_只出现关注者的帖子() throws Exception {
        Account followed = register("up_followed");
        Account other = register("up_other");
        Account viewer = register("up_viewer");
        createPost(followed, "来自被关注者的帖子zzf1");
        createPost(other, "来自陌生人的帖子zzf2");

        mockMvc.perform(post("/api/v1/users/" + followed.id() + "/follow")
                        .header("Authorization", "Bearer " + viewer.token()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/posts")
                        .param("feed", "following")
                        .header("Authorization", "Bearer " + viewer.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].title").value("来自被关注者的帖子zzf1"));
    }

    @Test
    void 主页_本人可见待审核_他人不可见() throws Exception {
        Account author = register("up_pending");
        Account other = register("up_other2");

        // 用 DRAFT 造一条非 ONLINE 帖子
        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + author.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "草稿帖zzd1",
                                "content", "草稿",
                                "status", "DRAFT",
                                "topics", List.of(Map.of("topicId", PLACE_ID)))))
                )
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/" + author.id() + "/posts")
                        .header("Authorization", "Bearer " + author.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(get("/api/v1/users/" + author.id() + "/posts")
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
    }
}
