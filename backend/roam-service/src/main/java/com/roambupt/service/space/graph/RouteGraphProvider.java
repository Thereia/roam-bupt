package com.roambupt.service.space.graph;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.service.space.plan.RouteEdge;
import com.roambupt.service.space.plan.RouteGraph;
import com.roambupt.service.space.repository.entity.RouteEdgeDO;
import com.roambupt.service.space.repository.entity.RouteNodeDO;
import com.roambupt.service.space.repository.mapper.RouteEdgeMapper;
import com.roambupt.service.space.repository.mapper.RouteNodeMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从库载图并缓存。图是静态数据，缓存 5 分钟，重启后懒加载。
 */
@Component
public class RouteGraphProvider {

    private static final Logger log = LoggerFactory.getLogger(RouteGraphProvider.class);
    private static final Duration TTL = Duration.ofMinutes(5);

    private final RouteNodeMapper nodeMapper;
    private final RouteEdgeMapper edgeMapper;

    private volatile RouteGraph cached;
    private volatile Instant loadedAt = Instant.EPOCH;

    public RouteGraphProvider(RouteNodeMapper nodeMapper, RouteEdgeMapper edgeMapper) {
        this.nodeMapper = nodeMapper;
        this.edgeMapper = edgeMapper;
    }

    public RouteGraph graph() {
        if (cached == null || Instant.now().isAfter(loadedAt.plus(TTL))) {
            synchronized (this) {
                if (cached == null || Instant.now().isAfter(loadedAt.plus(TTL))) {
                    cached = load();
                    loadedAt = Instant.now();
                }
            }
        }
        return cached;
    }

    private RouteGraph load() {
        List<RouteNodeDO> nodes = nodeMapper.selectList(Wrappers.<RouteNodeDO>lambdaQuery()
                .eq(RouteNodeDO::getEnabled, 1));
        List<RouteEdgeDO> edges = edgeMapper.selectList(Wrappers.<RouteEdgeDO>lambdaQuery()
                .eq(RouteEdgeDO::getEnabled, 1));

        Map<Long, String> names = new HashMap<>();
        for (RouteNodeDO node : nodes) {
            names.put(node.getNodeId(), node.getName());
        }
        List<RouteEdge> routeEdges = new ArrayList<>();
        for (RouteEdgeDO edge : edges) {
            routeEdges.add(new RouteEdge(edge.getEdgeId(), edge.getFromNodeId(), edge.getToNodeId(),
                    edge.getDistanceMeters(), edge.getTimeSeconds(), edge.getEdgeType(),
                    edge.getCongestionFactor() == null ? 1.0 : edge.getCongestionFactor().doubleValue()));
        }
        log.info("space.graph.loaded nodes={} edges={}", names.size(), routeEdges.size());
        return RouteGraph.of(names, routeEdges);
    }
}
