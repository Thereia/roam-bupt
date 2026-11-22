package com.roambupt.service.space.service.impl;

import com.roambupt.api.model.dto.RouteQuery;
import com.roambupt.api.model.dto.RouteStopRequest;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.MapEdgeVO;
import com.roambupt.api.model.vo.MapNodeVO;
import com.roambupt.api.model.vo.MapVO;
import com.roambupt.api.model.vo.RoutePlanVO;
import com.roambupt.api.model.vo.RouteSegmentVO;
import com.roambupt.service.space.graph.RouteGraphProvider;
import com.roambupt.service.space.plan.RouteGraph;
import com.roambupt.service.space.plan.RoutePlan;
import com.roambupt.service.space.plan.RoutePlanner;
import com.roambupt.service.space.plan.RouteStop;
import com.roambupt.service.space.plan.TravelMode;
import com.roambupt.service.space.service.SpaceService;
import com.roambupt.service.space.repository.entity.RouteEdgeDO;
import com.roambupt.service.space.repository.entity.RouteNodeDO;
import com.roambupt.service.space.repository.mapper.RouteEdgeMapper;
import com.roambupt.service.space.repository.mapper.RouteNodeMapper;
import com.roambupt.service.space.traffic.TrafficRecorder;
import com.roambupt.service.topic.service.TopicService;
import org.apache.commons.lang3.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 路线规划：解析主题 → 节点，寻路，记录拥挤度，映射成 VO。 */
@Service
public class SpaceServiceImpl implements SpaceService {

    private static final int MAX_STOPS = 6;
    private static final int DEFAULT_ALTERNATIVES = 3;

    private final RouteGraphProvider graphProvider;
    private final RoutePlanner routePlanner;
    private final TrafficRecorder trafficRecorder;
    private final TopicService topicService;
    private final RouteNodeMapper routeNodeMapper;
    private final RouteEdgeMapper routeEdgeMapper;

    public SpaceServiceImpl(RouteGraphProvider graphProvider, RoutePlanner routePlanner,
                            TrafficRecorder trafficRecorder, TopicService topicService,
                            RouteNodeMapper routeNodeMapper, RouteEdgeMapper routeEdgeMapper) {
        this.graphProvider = graphProvider;
        this.routePlanner = routePlanner;
        this.trafficRecorder = trafficRecorder;
        this.topicService = topicService;
        this.routeNodeMapper = routeNodeMapper;
        this.routeEdgeMapper = routeEdgeMapper;
    }

    @Override
    public MapVO map() {
        var nodes = routeNodeMapper.selectList(Wrappers.<RouteNodeDO>lambdaQuery()
                        .eq(RouteNodeDO::getEnabled, 1))
                .stream()
                .map(node -> new MapNodeVO(node.getNodeId(), node.getName(), node.getNodeType(),
                        node.getXRatio().doubleValue(), node.getYRatio().doubleValue()))
                .toList();
        var edges = routeEdgeMapper.selectList(Wrappers.<RouteEdgeDO>lambdaQuery()
                        .eq(RouteEdgeDO::getEnabled, 1))
                .stream()
                .map(edge -> new MapEdgeVO(edge.getFromNodeId(), edge.getToNodeId(),
                        edge.getEdgeType(), edge.getDistanceMeters()))
                .toList();
        return new MapVO(nodes, edges);
    }

    @Override
    public Optional<RoutePlanVO> plan(RouteQuery query) {
        List<RoutePlanVO> plans = doPlan(query, 1);
        return plans.isEmpty() ? Optional.empty() : Optional.of(plans.get(0));
    }

    @Override
    public List<RoutePlanVO> alternatives(RouteQuery query, int limit) {
        return doPlan(query, Math.min(Math.max(1, limit), DEFAULT_ALTERNATIVES));
    }

    private List<RoutePlanVO> doPlan(RouteQuery query, int limit) {
        TravelMode mode = parseMode(query.mode());
        List<RouteStop> stops = resolveStops(query.stops());

        RouteGraph graph = graphProvider.graph();
        if (graph.names().isEmpty()) {
            throw new BusinessException(StatusEnum.SERVICE_UNAVAILABLE, "路网数据未就绪");
        }

        Map<Long, Double> factors = trafficRecorder.factors(allEdgeIds(graph));

        List<RoutePlan> plans = limit == 1
                ? routePlanner.plan(graph, stops, mode, factors).map(List::of).orElse(List.of())
                : routePlanner.alternatives(graph, stops, mode, factors, limit);
        if (plans.isEmpty()) {
            return List.of();
        }
        trafficRecorder.record(plans.get(0).edges().stream().map(edge -> edge.edgeId()).toList());
        return plans.stream().map(plan -> toVO(plan, graph, stops, factors)).toList();
    }

    private List<Long> allEdgeIds(RouteGraph graph) {
        Set<Long> ids = new LinkedHashSet<>();
        graph.adjacency().values().forEach(edges -> edges.forEach(edge -> ids.add(edge.edgeId())));
        return new ArrayList<>(ids);
    }

    private TravelMode parseMode(String mode) {
        if (StringUtils.isBlank(mode)) {
            return TravelMode.MIXED;
        }
        try {
            return TravelMode.valueOf(mode.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "mode 只能是 DISTANCE / TIME / MIXED");
        }
    }

    private List<RouteStop> resolveStops(List<RouteStopRequest> requested) {
        if (requested == null || requested.size() < 2) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "至少需要两个地点");
        }
        if (requested.size() > MAX_STOPS) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "最多 6 个地点");
        }
        List<RouteStop> stops = new ArrayList<>();
        for (RouteStopRequest item : requested) {
            if (item == null || item.topicId() == null) {
                throw new BusinessException(StatusEnum.PARAM_ERROR, "地点不能为空");
            }
            Long nodeId = topicService.findNodeId(item.topicId());
            if (nodeId == null) {
                throw new BusinessException(StatusEnum.PARAM_ERROR, "该地点暂不支持路线");
            }
            stops.add(new RouteStop(nodeId, ""));
        }
        return stops;
    }

    private RoutePlanVO toVO(RoutePlan plan, RouteGraph graph, List<RouteStop> stops,
                             Map<Long, Double> factors) {
        List<RouteSegmentVO> segments = new ArrayList<>();
        List<Long> nodeIds = new ArrayList<>();
        long current = stops.get(0).nodeId();
        nodeIds.add(current);
        for (var edge : plan.edges()) {
            long next = edge.from() == current ? edge.to() : edge.from();
            segments.add(new RouteSegmentVO(
                    graph.names().getOrDefault(current, String.valueOf(current)),
                    graph.names().getOrDefault(next, String.valueOf(next)),
                    edge.distanceMeters(), edge.timeSeconds(),
                    factors.getOrDefault(edge.edgeId(), 1.0), edge.edgeType()));
            nodeIds.add(next);
            current = next;
        }
        return new RoutePlanVO(segments, plan.distanceMeters(), plan.timeSeconds(),
                nodeIds, plan.maxCongestion());
    }
}
