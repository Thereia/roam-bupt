package com.roambupt.service.search.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roambupt.service.search.config.SearchProperties;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ES 召回：只返回 id 与总数，字段展示回读 MySQL。
 * ES 未开启时 {@link #search} 返回 null，由调用方降级。
 */
@Component
public class EsSearchGateway {

    private final SearchProperties properties;
    private final ObjectProvider<RestClient> restClientProvider;
    private final ObjectMapper objectMapper;

    public EsSearchGateway(SearchProperties properties,
                           ObjectProvider<RestClient> restClientProvider,
                           ObjectMapper objectMapper) {
        this.properties = properties;
        this.restClientProvider = restClientProvider;
        this.objectMapper = objectMapper;
    }

    /** 返回 null 表示未开启，调用方应降级。 */
    public SearchHits search(String keyword, Long topicId, int offset, int size) throws Exception {
        RestClient restClient = restClientProvider.getIfAvailable();
        if (restClient == null || !properties.enabled()) {
            return null;
        }
        Request request = new Request("POST", "/" + properties.index() + "/_search");
        request.setJsonEntity(objectMapper.writeValueAsString(buildQuery(keyword, topicId, offset, size)));
        Response response = restClient.performRequest(request);

        Map<?, ?> parsed = objectMapper.readValue(EntityUtils.toString(response.getEntity()), Map.class);
        Map<?, ?> hits = (Map<?, ?>) parsed.get("hits");
        Map<?, ?> totalNode = (Map<?, ?>) hits.get("total");
        long total = ((Number) totalNode.get("value")).longValue();

        List<Long> ids = new ArrayList<>();
        for (Object item : (List<?>) hits.get("hits")) {
            Map<?, ?> source = (Map<?, ?>) ((Map<?, ?>) item).get("_source");
            ids.add(((Number) source.get("postId")).longValue());
        }
        return new SearchHits(ids, total);
    }

    private Map<String, Object> buildQuery(String keyword, Long topicId, int offset, int size) {
        List<Object> filters = new ArrayList<>();
        filters.add(Map.of("term", Map.of("status", "ONLINE")));
        if (topicId != null) {
            filters.add(Map.of("term", Map.of("topicIds", topicId)));
        }
        Map<String, Object> bool = Map.of(
                "must", List.of(Map.of("multi_match",
                        Map.of("query", keyword, "fields", List.of("title^2", "content"),
                                "operator", "and"))),
                "filter", filters);
        return Map.of(
                "query", Map.of("bool", bool),
                "sort", List.of(Map.of("_score", "desc"), Map.of("createdTime", "desc")),
                "from", offset,
                "size", size);
    }

    /** 召回结果。 */
    public record SearchHits(List<Long> ids, long total) {
    }
}
