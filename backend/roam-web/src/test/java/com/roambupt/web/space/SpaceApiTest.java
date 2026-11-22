package com.roambupt.web.space;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roambupt.service.post.repository.entity.PostDO;
import com.roambupt.service.post.repository.entity.PostTopicDO;
import com.roambupt.service.post.repository.mapper.PostMapper;
import com.roambupt.service.post.repository.mapper.PostTopicMapper;
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

/** 路线接口：用真实路网数据（372 节点 / 446 边）。 */
@SpringBootTest(properties = {
        "roam.auth.login-limit-per-minute=1000",
        "roam.jwt.secret=test-secret-must-be-long-enough-32bytes",
        "roam.post.auto-approve=true",
        "roam.search.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class SpaceApiTest {

    /** 教三楼 与 学一食堂，两个都有 node_id 且连通。 */
    private static final long TEACHING = 79L;
    private static final long CANTEEN = 113L;

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
    private PostMapper postMapper;

    @Autowired
    private PostTopicMapper postTopicMapper;

    private String register(String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "secret123", "nickname", username))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/token").asText();
    }

    private String body(long... topicIds) throws Exception {
        List<Map<String, Object>> stops = java.util.Arrays.stream(topicIds)
                .mapToObj(id -> Map.<String, Object>of("topicId", id))
                .toList();
        return objectMapper.writeValueAsString(Map.of("stops", stops, "mode", "MIXED"));
    }

    @Test
    void 底图_返回全部启用节点与边() throws Exception {
        mockMvc.perform(get("/api/v1/nav/map"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes.length()").value(org.hamcrest.Matchers.greaterThan(300)))
                .andExpect(jsonPath("$.data.edges.length()").value(org.hamcrest.Matchers.greaterThan(400)))
                .andExpect(jsonPath("$.data.nodes[0].x").value(org.hamcrest.Matchers.greaterThan(0.0)));
    }

    @Test
    void 路线_两个地点_返回分段与总耗时() throws Exception {
        mockMvc.perform(post("/api/v1/nav/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TEACHING, CANTEEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.segments.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.totalDistanceMeters").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.totalTimeSeconds").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void 路线_只有一个地点_返回4001() throws Exception {
        mockMvc.perform(post("/api/v1/nav/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TEACHING)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void 路线_地点不存在_返回4001() throws Exception {
        mockMvc.perform(post("/api/v1/nav/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TEACHING, 999999L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void 路线_超过6个地点_返回4001() throws Exception {
        mockMvc.perform(post("/api/v1/nav/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TEACHING, CANTEEN, TEACHING, CANTEEN, TEACHING, CANTEEN, TEACHING)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void 多方案_返回按耗时排序的列表() throws Exception {
        mockMvc.perform(post("/api/v1/nav/routes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(TEACHING, CANTEEN))
                        .param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data[0].totalTimeSeconds").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void 帖子路线_按地点顺序串联() throws Exception {
        String token = register("sp_author");
        String createBody = objectMapper.writeValueAsString(Map.of(
                "title", "路线串联测试帖",
                "content", "从教三走到食堂",
                "topics", List.of(Map.of("topicId", TEACHING, "sortNo", 0),
                        Map.of("topicId", CANTEEN, "sortNo", 1))));
        String response = mockMvc.perform(post("/api/v1/posts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long postId = objectMapper.readTree(response).at("/data/id").asLong();

        mockMvc.perform(get("/api/v1/posts/" + postId + "/route"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodeIds.length()").value(org.hamcrest.Matchers.greaterThan(1)));
    }

    @Test
    void 帖子路线_没有地点_返回4001() throws Exception {
        // 发帖接口强制至少一个地点，这里直接落一条只有分类的帖子来测这条路
        String categoryBody = mockMvc.perform(get("/api/v1/topics").param("type", "CATEGORY"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long category = objectMapper.readTree(categoryBody).at("/data/list/0/id").asLong();

        PostDO post = new PostDO();
        post.setUserId(1L);
        post.setTitle("只有分类的帖子");
        post.setContent("没有地点");
        post.setStatus("ONLINE");
        post.setHeat(0);
        post.setCreatedTime(java.time.LocalDateTime.now());
        postMapper.insert(post);

        PostTopicDO link = new PostTopicDO();
        link.setPostId(post.getId());
        link.setTopicId(category);
        link.setSortNo(0);
        postTopicMapper.insert(link);

        mockMvc.perform(get("/api/v1/posts/" + post.getId() + "/route"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }
}
