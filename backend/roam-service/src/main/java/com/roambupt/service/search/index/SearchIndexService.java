package com.roambupt.service.search.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roambupt.service.post.repository.entity.PostDO;
import com.roambupt.service.post.repository.mapper.PostMapper;
import com.roambupt.service.post.repository.mapper.PostTopicMapper;
import com.roambupt.service.post.repository.entity.PostTopicDO;
import com.roambupt.service.search.config.SearchProperties;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 索引维护：建索引、写文档、删文档。
 *
 * <p>只负责 ES 侧；调用方保证传入的帖子状态正确。</p>
 */
@Service
public class SearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexService.class);
    private static final String STATUS_ONLINE = "ONLINE";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final SearchProperties properties;
    private final ObjectProvider<RestClient> restClientProvider;
    private final ObjectMapper objectMapper;
    private final PostMapper postMapper;
    private final PostTopicMapper postTopicMapper;

    public SearchIndexService(SearchProperties properties,
                              ObjectProvider<RestClient> restClientProvider,
                              ObjectMapper objectMapper,
                              PostMapper postMapper,
                              PostTopicMapper postTopicMapper) {
        this.properties = properties;
        this.restClientProvider = restClientProvider;
        this.objectMapper = objectMapper;
        this.postMapper = postMapper;
        this.postTopicMapper = postTopicMapper;
    }

    public boolean enabled() {
        return properties.enabled() && restClientProvider.getIfAvailable() != null;
    }

    /** 建索引（已存在则跳过）。 */
    public void ensureIndex() {
        if (!enabled()) {
            return;
        }
        RestClient client = restClientProvider.getIfAvailable();
        try {
            Request head = new Request("HEAD", "/" + properties.index());
            Response response = client.performRequest(head);
            if (response.getStatusLine().getStatusCode() == 200) {
                return;
            }
        } catch (Exception e) {
            log.info("search.index.not_found index={}", properties.index());
        }
        try {
            Map<String, Object> mappings = Map.of(
                    "properties", Map.of(
                            "postId", Map.of("type", "long"),
                            "title", Map.of("type", "text"),
                            "content", Map.of("type", "text"),
                            "authorId", Map.of("type", "long"),
                            "topicIds", Map.of("type", "long"),
                            "status", Map.of("type", "keyword"),
                            "createdTime", Map.of("type", "date")));
            Request create = new Request("PUT", "/" + properties.index());
            create.setJsonEntity(objectMapper.writeValueAsString(Map.of("mappings", mappings)));
            client.performRequest(create);
            log.info("search.index.created index={}", properties.index());
        } catch (Exception e) {
            log.warn("search.index.create.failed index={} reason={}", properties.index(), e.getMessage());
        }
    }

    /**
     * 只改状态，不重算全文：上下线、审核通过都用它。
     * 文档不存在时退化成整篇重建。
     */
    public void updateStatus(long postId, String status) {
        if (!enabled()) {
            return;
        }
        // 只索引公开内容：下线/删除/待审一律移除文档
        if (!"ONLINE".equals(status)) {
            delete(postId);
            return;
        }
        try {
            RestClient client = restClientProvider.getIfAvailable();
            if (client == null) {
                return;
            }
            Request request = new Request("POST", "/" + properties.index() + "/_update/" + postId);
            request.setJsonEntity(objectMapper.writeValueAsString(
                    Map.of("doc", Map.of("status", status))));
            client.performRequest(request);
        } catch (Exception e) {
            log.warn("search.index.status.failed postId={} status={} reason={}", postId, status, e.getMessage());
            index(postId);
        }
    }

    /** 写一条文档；帖子不在线时删除。 */
    public void index(long postId) {
        if (!enabled()) {
            return;
        }
        PostDO post = postMapper.selectById(postId);
        if (post == null || !STATUS_ONLINE.equals(post.getStatus())) {
            delete(postId);
            return;
        }
        List<Long> topicIds = postTopicMapper.selectList(Wrappers.<PostTopicDO>lambdaQuery()
                        .eq(PostTopicDO::getPostId, postId))
                .stream().map(PostTopicDO::getTopicId).toList();

        Map<String, Object> doc = new HashMap<>();
        doc.put("postId", post.getId());
        doc.put("title", post.getTitle());
        doc.put("content", post.getContent());
        doc.put("authorId", post.getUserId());
        doc.put("topicIds", topicIds);
        doc.put("status", post.getStatus());
        doc.put("createdTime", post.getCreatedTime().format(TIME_FORMAT));

        try {
            Request request = new Request("PUT", "/" + properties.index() + "/_doc/" + postId);
            request.setJsonEntity(objectMapper.writeValueAsString(doc));
            restClientProvider.getIfAvailable().performRequest(request);
        } catch (Exception e) {
            log.warn("search.index.write.failed postId={} reason={}", postId, e.getMessage());
        }
    }

    public void delete(long postId) {
        if (!enabled()) {
            return;
        }
        try {
            Request request = new Request("DELETE", "/" + properties.index() + "/_doc/" + postId);
            restClientProvider.getIfAvailable().performRequest(request);
        } catch (Exception e) {
            log.warn("search.index.delete.failed postId={} reason={}", postId, e.getMessage());
        }
    }

    /** 全量重建：启动后调用，逐条 upsert。 */
    public void rebuildAll() {
        if (!enabled()) {
            return;
        }
        ensureIndex();
        List<PostDO> posts = postMapper.selectList(Wrappers.<PostDO>lambdaQuery()
                .eq(PostDO::getStatus, STATUS_ONLINE));
        for (PostDO post : posts) {
            index(post.getId());
        }
        log.info("search.index.rebuilt count={}", posts.size());
    }
}
