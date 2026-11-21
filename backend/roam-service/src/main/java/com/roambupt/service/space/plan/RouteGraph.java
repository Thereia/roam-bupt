package com.roambupt.service.space.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 无向图（边双向可走）。构建时把每条边同时登记到 from 和 to 的邻接表。
 */
public record RouteGraph(Map<Long, String> names, Map<Long, List<RouteEdge>> adjacency) {

    public static RouteGraph of(Map<Long, String> names, List<RouteEdge> edges) {
        Map<Long, List<RouteEdge>> adjacency = new HashMap<>();
        for (RouteEdge edge : edges) {
            adjacency.computeIfAbsent(edge.from(), key -> new ArrayList<>()).add(edge);
            adjacency.computeIfAbsent(edge.to(), key -> new ArrayList<>()).add(edge);
        }
        return new RouteGraph(Map.copyOf(names), Map.copyOf(adjacency));
    }

    public List<RouteEdge> edgesFrom(long nodeId) {
        return adjacency.getOrDefault(nodeId, List.of());
    }

    public boolean contains(long nodeId) {
        return names.containsKey(nodeId);
    }
}
