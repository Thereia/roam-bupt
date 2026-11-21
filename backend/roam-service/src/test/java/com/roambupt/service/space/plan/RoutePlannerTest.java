package com.roambupt.service.space.plan;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RoutePlanner 的行为规格：纯函数，只吃图和数据。
 *
 * <p>小图（A=1, B=2, C=3, D=4）：
 * A-B 100m/100s，B-D 100m/100s，A-C 300m/300s，C-D 300m/300s，B-C 50m/500s（近但慢）</p>
 */
class RoutePlannerTest {

    private final RoutePlanner planner = new DijkstraRoutePlanner();

    private RouteGraph graph() {
        return RouteGraph.of(
                Map.of(1L, "A", 2L, "B", 3L, "C", 4L, "D", 5L, "孤岛"),
                List.of(
                        edge(10, 1, 2, 100, 100),
                        edge(11, 2, 4, 100, 100),
                        edge(12, 1, 3, 300, 300),
                        edge(13, 3, 4, 300, 300),
                        edge(14, 2, 3, 50, 500)));
    }

    private RouteEdge edge(long id, long from, long to, int meters, int seconds) {
        return new RouteEdge(id, from, to, meters, seconds, "ROAD", 1.0);
    }

    private List<RouteStop> stops(long... ids) {
        return java.util.Arrays.stream(ids)
                .mapToObj(id -> new RouteStop(id, "n" + id))
                .toList();
    }

    @Test
    void 按距离_选总距离最短的路() {
        Optional<RoutePlan> plan = planner.plan(graph(), stops(1, 4), TravelMode.DISTANCE, Map.of());

        assertThat(plan).isPresent();
        assertThat(plan.get().distanceMeters()).isEqualTo(200);
        assertThat(plan.get().edges()).extracting(RouteEdge::edgeId).containsExactly(10L, 11L);
    }

    @Test
    void 按时间_近但慢的边不会被选中() {
        // B-C 只有 50 米，但要 500 秒；时间权重下应走 A-B-D
        Optional<RoutePlan> plan = planner.plan(graph(), stops(1, 4), TravelMode.TIME, Map.of());

        assertThat(plan).isPresent();
        assertThat(plan.get().timeSeconds()).isEqualTo(200);
        assertThat(plan.get().edges()).extracting(RouteEdge::edgeId).containsExactly(10L, 11L);
    }

    @Test
    void 拥挤度放大后_绕开原本最快的路() {
        // B-D 的拥挤系数 ×5：100 + 500 = 600 秒，A-C-D 仍是 600，故改为验证 ×10
        Optional<RoutePlan> plan = planner.plan(graph(), stops(1, 4), TravelMode.TIME,
                Map.of(11L, 10.0));

        assertThat(plan).isPresent();
        assertThat(plan.get().edges()).extracting(RouteEdge::edgeId).containsExactly(12L, 13L);
        assertThat(plan.get().timeSeconds()).isEqualTo(600);
    }

    @Test
    void 多个地点_按顺序串成多段() {
        Optional<RoutePlan> plan = planner.plan(graph(), stops(1, 2, 4), TravelMode.TIME, Map.of());

        assertThat(plan).isPresent();
        assertThat(plan.get().edges()).extracting(RouteEdge::edgeId).containsExactly(10L, 11L);
        assertThat(plan.get().distanceMeters()).isEqualTo(200);
    }

    @Test
    void 不连通_返回空() {
        Optional<RoutePlan> plan = planner.plan(graph(), stops(1, 5), TravelMode.TIME, Map.of());

        assertThat(plan).isEmpty();
    }

    @Test
    void 备选方案_禁掉最优路径的第一条边后给出绕行() {
        List<RoutePlan> plans = planner.alternatives(graph(), stops(1, 4), TravelMode.TIME, Map.of(), 3);

        assertThat(plans).hasSizeGreaterThanOrEqualTo(2);
        assertThat(plans.get(0).edges()).extracting(RouteEdge::edgeId).containsExactly(10L, 11L);
        assertThat(plans.stream().map(p -> p.edges().stream().map(RouteEdge::edgeId).toList()))
                .contains(List.of(12L, 13L));
    }

    @Test
    void 权重计算_混合模式用时间乘拥挤系数() {
        RouteEdge e = edge(20, 1, 2, 200, 100);
        assertThat(planner.weightOf(e, TravelMode.DISTANCE, Map.of())).isEqualTo(200.0);
        assertThat(planner.weightOf(e, TravelMode.TIME, Map.of())).isEqualTo(100.0);
        assertThat(planner.weightOf(e, TravelMode.MIXED, Map.of(20L, 2.0))).isEqualTo(200.0);
    }
}
