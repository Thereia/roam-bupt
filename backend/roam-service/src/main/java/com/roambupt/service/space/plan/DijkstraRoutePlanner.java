package com.roambupt.service.space.plan;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Dijkstra 实现。无向图，多地点按顺序逐段求解后拼接。
 *
 * <p>多方案做法：先求最优；再逐条禁用最优路径上的边重算，去重后取前 limit 条。
 * 不做 k 短路算法——这个规模下禁边重算足够，且更容易解释。</p>
 */
@Component
public class DijkstraRoutePlanner implements RoutePlanner {

    private static final double DEFAULT_FACTOR = 1.0;

    @Override
    public Optional<RoutePlan> plan(RouteGraph graph, List<RouteStop> stops, TravelMode mode,
                                    Map<Long, Double> edgeFactors) {
        return planAvoiding(graph, stops, mode, edgeFactors, Set.of());
    }

    @Override
    public List<RoutePlan> alternatives(RouteGraph graph, List<RouteStop> stops, TravelMode mode,
                                        Map<Long, Double> edgeFactors, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<RoutePlan> plans = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        plan(graph, stops, mode, edgeFactors).ifPresent(best -> {
            plans.add(best);
            seen.add(signature(best));
        });
        if (plans.isEmpty()) {
            return List.of();
        }

        for (RouteEdge edge : plans.get(0).edges()) {
            if (plans.size() >= limit) {
                break;
            }
            Set<Long> banned = new HashSet<>();
            banned.add(edge.edgeId());
            planAvoiding(graph, stops, mode, edgeFactors, banned).ifPresent(candidate -> {
                if (seen.add(signature(candidate))) {
                    plans.add(candidate);
                }
            });
        }

        plans.sort(Comparator.comparingDouble(p -> totalWeight(p, mode, edgeFactors)));
        return plans.size() > limit ? plans.subList(0, limit) : plans;
    }

    @Override
    public double weightOf(RouteEdge edge, TravelMode mode, Map<Long, Double> edgeFactors) {
        double factor = edgeFactors.getOrDefault(edge.edgeId(), DEFAULT_FACTOR);
        return switch (mode) {
            case DISTANCE -> edge.distanceMeters();
            case TIME, MIXED -> edge.timeSeconds() * edge.congestionFactor() * factor;
        };
    }

    private Optional<RoutePlan> planAvoiding(RouteGraph graph, List<RouteStop> stops, TravelMode mode,
                                             Map<Long, Double> edgeFactors, Set<Long> banned) {
        if (stops == null || stops.size() < 2) {
            return Optional.empty();
        }
        List<RouteEdge> edges = new ArrayList<>();
        for (int i = 0; i < stops.size() - 1; i++) {
            Optional<List<RouteEdge>> leg = shortestPath(graph, stops.get(i).nodeId(),
                    stops.get(i + 1).nodeId(), mode, edgeFactors, banned);
            if (leg.isEmpty()) {
                return Optional.empty();
            }
            edges.addAll(leg.get());
        }
        return Optional.of(summarize(edges, edgeFactors));
    }

    private Optional<List<RouteEdge>> shortestPath(RouteGraph graph, long from, long to,
                                                   TravelMode mode, Map<Long, Double> edgeFactors,
                                                   Set<Long> banned) {
        if (from == to) {
            return Optional.of(List.of());
        }
        if (!graph.contains(from) || !graph.contains(to)) {
            return Optional.empty();
        }

        Map<Long, Double> dist = new HashMap<>();
        Map<Long, RouteEdge> prevEdge = new HashMap<>();
        PriorityQueue<long[]> queue = new PriorityQueue<>(Comparator.comparingDouble(a -> Double.longBitsToDouble(a[1])));
        dist.put(from, 0.0);
        queue.add(new long[]{from, Double.doubleToLongBits(0.0)});
        Set<Long> settled = new LinkedHashSet<>();

        while (!queue.isEmpty()) {
            long[] head = queue.poll();
            long node = head[0];
            double cost = Double.longBitsToDouble(head[1]);
            if (!settled.add(node)) {
                continue;
            }
            if (node == to) {
                break;
            }
            for (RouteEdge edge : graph.edgesFrom(node)) {
                if (banned.contains(edge.edgeId())) {
                    continue;
                }
                long next = edge.from() == node ? edge.to() : edge.from();
                double candidate = cost + weightOf(edge, mode, edgeFactors);
                Double known = dist.get(next);
                if (known == null || candidate < known) {
                    dist.put(next, candidate);
                    prevEdge.put(next, edge);
                    queue.add(new long[]{next, Double.doubleToLongBits(candidate)});
                }
            }
        }

        if (!dist.containsKey(to)) {
            return Optional.empty();
        }
        List<RouteEdge> path = new ArrayList<>();
        long cursor = to;
        while (cursor != from) {
            RouteEdge edge = prevEdge.get(cursor);
            if (edge == null) {
                return Optional.empty();
            }
            path.add(edge);
            cursor = edge.from() == cursor ? edge.to() : edge.from();
        }
        java.util.Collections.reverse(path);
        return Optional.of(path);
    }

    private RoutePlan summarize(List<RouteEdge> edges, Map<Long, Double> edgeFactors) {
        int distance = 0;
        int time = 0;
        double maxCongestion = DEFAULT_FACTOR;
        for (RouteEdge edge : edges) {
            distance += edge.distanceMeters();
            time += edge.timeSeconds();
            maxCongestion = Math.max(maxCongestion, edgeFactors.getOrDefault(edge.edgeId(), DEFAULT_FACTOR));
        }
        return new RoutePlan(List.copyOf(edges), distance, time, maxCongestion);
    }

    private double totalWeight(RoutePlan plan, TravelMode mode, Map<Long, Double> edgeFactors) {
        return plan.edges().stream().mapToDouble(edge -> weightOf(edge, mode, edgeFactors)).sum();
    }

    private String signature(RoutePlan plan) {
        return plan.edges().stream().map(edge -> String.valueOf(edge.edgeId()))
                .reduce("", (a, b) -> a + "-" + b);
    }
}
