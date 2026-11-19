package com.roambupt.web.admin;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roambupt.service.user.repository.entity.UserDO;
import com.roambupt.service.user.repository.mapper.UserMapper;
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

/** 后台权限、审核状态机、封禁、敏感词。 */
@SpringBootTest(properties = {
        "roam.auth.login-limit-per-minute=1000",
        "roam.jwt.secret=test-secret-must-be-long-enough-32bytes",
        "roam.post.auto-approve=false",
        "roam.search.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class AdminApiTest {

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
    private UserMapper userMapper;

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

    private Account registerAdmin(String username) throws Exception {
        Account account = register(username);
        userMapper.update(null, Wrappers.<UserDO>lambdaUpdate()
                .eq(UserDO::getId, account.id())
                .set(UserDO::getRole, "ADMIN"));
        return account;
    }

    private long createPost(Account account, String title, String content) throws Exception {
        String response = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + account.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", title, "content", content,
                                "topics", List.of(Map.of("topicId", PLACE_ID)))))
                )
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    @Test
    void 后台_普通用户_403() throws Exception {
        Account user = register("adm_user1");
        mockMvc.perform(get("/api/v1/admin/posts").header("Authorization", "Bearer " + user.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void 后台_管理员_200() throws Exception {
        Account admin = registerAdmin("adm_admin1");
        mockMvc.perform(get("/api/v1/admin/posts").header("Authorization", "Bearer " + admin.token()))
                .andExpect(status().isOk());
    }

    @Test
    void 审核_PENDING转ONLINE_匿名可见() throws Exception {
        Account admin = registerAdmin("adm_admin2");
        Account author = register("adm_author2");
        long postId = createPost(author, "待审核帖子zza1", "内容");

        mockMvc.perform(put("/api/v1/admin/posts/" + postId + "/status")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ONLINE"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/posts/" + postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ONLINE"));
    }

    @Test
    void 审核_非法迁移_409() throws Exception {
        Account admin = registerAdmin("adm_admin3");
        Account author = register("adm_author3");
        long postId = createPost(author, "删了再上线zza2", "内容");

        mockMvc.perform(put("/api/v1/admin/posts/" + postId + "/status")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "DELETED"))))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/admin/posts/" + postId + "/status")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "ONLINE"))))
                .andExpect(status().isConflict());
    }

    @Test
    void 封禁_旧token失效_401() throws Exception {
        Account admin = registerAdmin("adm_admin4");
        Account victim = register("adm_victim4");

        mockMvc.perform(put("/api/v1/admin/users/" + victim.id() + "/status")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", 0))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + victim.token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 封禁自己_4001() throws Exception {
        Account admin = registerAdmin("adm_admin5");
        mockMvc.perform(put("/api/v1/admin/users/" + admin.id() + "/status")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void 发帖含敏感词_4001() throws Exception {
        Account admin = registerAdmin("adm_admin6");
        Account author = register("adm_author6");
        mockMvc.perform(post("/api/v1/admin/sensitive-words")
                        .header("Authorization", "Bearer " + admin.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("word", "违禁词"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + author.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "title", "正常标题",
                                "content", "这里包含违禁词",
                                "topics", List.of(Map.of("topicId", PLACE_ID)))))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }
}
