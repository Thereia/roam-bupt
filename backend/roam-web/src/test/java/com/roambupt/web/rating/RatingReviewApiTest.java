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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 地点评价流：文字、图片、作者、分页与删除后的回落。 */
@SpringBootTest(properties = {
        "roam.auth.login-limit-per-minute=1000",
        "roam.jwt.secret=test-secret-must-be-long-enough-32bytes",
        "roam.post.auto-approve=true",
        "roam.search.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class RatingReviewApiTest {

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

    private ResultActions putReview(String token, long topicId, Map<String, Object> body) throws Exception {
        return mockMvc.perform(put("/api/v1/topics/" + topicId + "/ratings")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private Map<String, Object> review(int score, String text, List<String> images) {
        Map<String, Object> body = new HashMap<>();
        body.put("score", score);
        body.put("review", text);
        body.put("images", images);
        return body;
    }

    @Test
    void 评价_文字图片作者_匿名可见() throws Exception {
        String token = register("rr_author1");
        putReview(token, PLACE, review(5, "三楼自习很安静", List.of("/files/a.jpg", "/files/b.jpg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(get("/api/v1/topics/" + PLACE + "/ratings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.count").value(1))
                .andExpect(jsonPath("$.data.summary.average").value(closeTo(5.0, 0.01)))
                .andExpect(jsonPath("$.data.summary.myScore").value(nullValue()))
                .andExpect(jsonPath("$.data.reviews.total").value(1))
                .andExpect(jsonPath("$.data.reviews.list", hasSize(1)))
                .andExpect(jsonPath("$.data.reviews.list[0].score").value(5))
                .andExpect(jsonPath("$.data.reviews.list[0].review").value("三楼自习很安静"))
                .andExpect(jsonPath("$.data.reviews.list[0].images", hasSize(2)))
                .andExpect(jsonPath("$.data.reviews.list[0].images[0]").value("/files/a.jpg"))
                .andExpect(jsonPath("$.data.reviews.list[0].author.nickname").value("rr_author1"))
                .andExpect(jsonPath("$.data.reviews.list[0].mine").value(false))
                .andExpect(jsonPath("$.data.reviews.list[0].createdTime").isNotEmpty());
    }

    @Test
    void 评价_只打分不写文字_也能出现在列表() throws Exception {
        String token = register("rr_bare");
        putReview(token, PLACE, Map.of("score", 4)).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/topics/" + PLACE + "/ratings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews.total").value(1))
                .andExpect(jsonPath("$.data.reviews.list[0].score").value(4))
                .andExpect(jsonPath("$.data.reviews.list[0].images", hasSize(0)));
    }

    @Test
    void 修改评价_文字图片更新_仍只有一条() throws Exception {
        String token = register("rr_edit");
        putReview(token, PLACE, review(5, "第一次", List.of("/files/one.jpg"))).andExpect(status().isOk());
        putReview(token, PLACE, review(2, "改主意了", List.of("/files/two.jpg", "/files/three.jpg")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));

        mockMvc.perform(get("/api/v1/topics/" + PLACE + "/ratings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.myScore").value(2))
                .andExpect(jsonPath("$.data.reviews.total").value(1))
                .andExpect(jsonPath("$.data.reviews.list[0].review").value("改主意了"))
                .andExpect(jsonPath("$.data.reviews.list[0].mine").value(true))
                .andExpect(jsonPath("$.data.reviews.list[0].images", hasSize(2)));
    }

    @Test
    void 评价文字超过200字_返回4001() throws Exception {
        String token = register("rr_long");
        putReview(token, PLACE, review(5, "字".repeat(201), List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void 图片超过3张_返回4001() throws Exception {
        String token = register("rr_many");
        putReview(token, PLACE, review(5, "图多", List.of(
                "/files/1.jpg", "/files/2.jpg", "/files/3.jpg", "/files/4.jpg")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void 图片不是站内路径_返回4001() throws Exception {
        String token = register("rr_badurl");
        putReview(token, PLACE, review(5, "外链", List.of("https://example.com/a.jpg")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(4001));
    }

    @Test
    void 删除评价_列表清空_均分人数回落() throws Exception {
        String token = register("rr_delete");
        putReview(token, PLACE, review(5, "先评再删", List.of("/files/a.jpg"))).andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/topics/" + PLACE + "/ratings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.average").value(closeTo(0.0, 0.001)));

        mockMvc.perform(get("/api/v1/topics/" + PLACE + "/ratings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.count").value(0))
                .andExpect(jsonPath("$.data.summary.average").value(closeTo(0.0, 0.001)))
                .andExpect(jsonPath("$.data.reviews.total").value(0))
                .andExpect(jsonPath("$.data.reviews.list", hasSize(0)));
    }

    @Test
    void 评价列表_分页_按最新在前() throws Exception {
        String first = register("rr_page1");
        String second = register("rr_page2");
        putReview(first, PLACE, review(5, "先来的", List.of())).andExpect(status().isOk());
        putReview(second, PLACE, review(3, "后到的", List.of())).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/topics/" + PLACE + "/ratings")
                        .param("page", "1").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews.total").value(2))
                .andExpect(jsonPath("$.data.reviews.page").value(1))
                .andExpect(jsonPath("$.data.reviews.pageSize").value(1))
                .andExpect(jsonPath("$.data.reviews.list", hasSize(1)))
                .andExpect(jsonPath("$.data.reviews.list[0].author.nickname").value("rr_page2"));

        mockMvc.perform(get("/api/v1/topics/" + PLACE + "/ratings")
                        .param("page", "2").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviews.list", hasSize(1)))
                .andExpect(jsonPath("$.data.reviews.list[0].author.nickname").value("rr_page1"));
    }

    @Test
    void 未登录提交评价_返回401() throws Exception {
        mockMvc.perform(put("/api/v1/topics/" + PLACE + "/ratings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("score", 5, "review", "匿名"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 分类不能评价_返回404() throws Exception {
        String token = register("rr_category");
        putReview(token, CATEGORY, review(5, "分类不是地点", List.of()))
                .andExpect(status().isNotFound());
    }
}
