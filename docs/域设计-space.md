# 域设计：space（路线与拥挤度）

> 状态与验收见 [实现清单](实现清单.md)；本文只写调用关系、接口、数据、协调与失败语义。

> 范围：路网数据、寻路算法、多地点串联、拥挤度记录与衰减、路线接口。
> 不含：逐转向导航、实时 GPS、底图配准、室内楼层。

## 1. 调用关系

```
RouteController
 ├─ POST /api/v1/nav/route        → SpaceService.plan(request)
 ├─ POST /api/v1/nav/routes       → SpaceService.alternatives(request)
 └─ GET  /posts/{id}/route        → PostRouteService.routeOfPost(postId)

SpaceServiceImpl
 ├─ RouteGraphProvider.load()          读 route_node / route_edge，进程内缓存
 ├─ RoutePlanner.plan(graph, stops, mode)   纯函数，无 Spring 依赖
 ├─ RouteTrafficRecorder.record(path)   Redis 分钟桶，失败不影响返回
 └─ TtlCache                           同起终点的短缓存（60s）

PostRouteService
 ├─ PostTopicQuery.listByPost(postId)   取有序地点（post 域提供）
 ├─ TopicQuery.findNodeId(topicId)      地点 → 图入口节点（topic 域提供）
 └─ SpaceService.plan(...)              每两个相邻地点算一段
```

## 2. 接口签名

```java
// API 层（roam-api）
public record RouteStopRequest(Long topicId, Integer sortNo) {}
public record RouteQuery(List<RouteStopRequest> stops, String mode) {}   // mode: DISTANCE/TIME/MIXED
public record RouteSegmentVO(Long fromNodeId, String fromName, Long toNodeId, String toName,
                             int distanceMeters, int timeSeconds, double congestion, String edgeType) {}
public record RoutePlanVO(List<RouteSegmentVO> segments, int totalDistanceMeters,
                          int totalTimeSeconds, List<Long> nodeIds, double maxCongestion) {}
public record RouteResponse(List<RoutePlanVO> plans) {}

// service 层
public record RouteStop(long nodeId, String name) {}
public record RouteEdge(long edgeId, long from, long to, int distance, int time,
                        String type, boolean bikeAllowed) {}
public record RouteGraph(Map<Long, List<RouteEdge>> adjacency, Map<Long, String> names) {}
public record RoutePlan(List<RouteEdge> edges, int distance, int time, double maxCongestion) {}

public interface RoutePlanner {
    RoutePlan plan(RouteGraph graph, List<RouteStop> stops, TravelMode mode);
    List<RoutePlan> alternatives(RouteGraph graph, List<RouteStop> stops, TravelMode mode, int limit);
}
```

`RoutePlanner` 是纯函数：入参是图和数据，出参是路径，不读库、不读 Redis、不打日志。测试时直接构造小图。

## 3. 数据与字段

- `route_node`：`node_id, name, type, x_ratio, y_ratio, building_id, floor, poi_category, enabled`
- `route_edge`：`edge_id, from_node_id, to_node_id, distance_meters, time_seconds, edge_type, bike_allowed, congestion_factor, enabled`
- 静态数据由 CSV 灌入（`docs/数据导出说明.md`），**不建 route 表**，路线每次现算。
- Redis key：`roam:traffic:{edgeId}:{yyyyMMddHHmm}`，TTL 5 分钟；缓存 key `roam:route:{mode}:{from}-{to}:{stopsHash}`，TTL 60 秒。

**拥挤度怎么算**（不落库，读时现算）：

```
u = 窗口内该边被请求次数 / 窗口内最忙边的请求次数     // 0..1，归一化
factor = 1 + K * u / (1 - u)                      // 漏桶放大，K 默认 1.0
边权 = time_seconds * factor                       // MIXED / TIME 模式
```

`u = 0` 时 factor = 1（不放大）；`u` 越接近 1，放大越快，最忙的边会明显变慢，方案自然绕开。
窗口默认 5 分钟，与 TTL 一致；`K` 与窗口都放配置，不写死。

## 4. 协调

**我从谁拿数据**

- `topic.node_id`：由 topic 域维护，space 只读；地点没有 `node_id` 时不进图，也不参与寻路。
- `post_topic.sort_no`：由 post 域维护，space 只读，用它决定多地点顺序，**不回写**。
- 两个域都通过 `*QueryService` 暴露查询方法，space 不 import 对方的 mapper。

**我给谁提供数据**

- 前端地图页需要：节点坐标（`x_ratio/y_ratio`）、边（用于画线）、方案分段（用于着色）。
- 提供方式：`GET /api/v1/nav/map` 返回精简图（只含 enabled 节点/边），与方案接口分开——图画一次，方案可多次请求。

**时序与幂等**

- 寻路成功 → 异步记录拥挤度；记录失败只打 WARN，**不影响响应**。
- 同一个用户连续点两次“按这条走”，拥挤度会记两次：这是有意的（问路次数就是信号），不做去重。

**跨域失败**

- topic 域不可用（查不到 node_id）→ 该地点按“不支持路线”处理（`4001`），不是 500。
- post 域拿不到帖子 → `404`；帖子没有地点 → `4001`。
- Redis 不可用 → 拥挤度按 0 处理，路线照常返回（降级不报错）。

## 5. 失败语义

| 场景 | 返回 |
|---|---|
| 起点或终点缺少 `node_id` | `4001`「该地点暂不支持路线」 |
| 起终点不连通 | `404`「两点之间没有可走的路」 |
| `stops` 为空或超过 6 个 | `4001` |
| `mode` 不在枚举内 | `4001` |
| 帖子没有地点主题 | `4001`「这条帖子没有地点」 |
| 图数据为空 | `503`「路网数据未就绪」 |

## 6. 前端契约

地图页一次拿图、按需拿方案：

```json
// GET /api/v1/nav/map
{ "nodes": [{"id": 12, "name": "教三楼", "x": 0.42, "y": 0.61, "type": "BUILDING"}],
  "edges": [{"from": 12, "to": 15, "type": "WALK", "distance": 180}] }

// POST /api/v1/nav/routes  { "stops": [{"topicId": 79}, {"topicId": 113}], "mode": "MIXED" }
{ "plans": [{ "segments": [{"fromName": "教三楼", "toName": "学一食堂",
    "distanceMeters": 420, "timeSeconds": 330, "congestion": 1.4}],
    "totalDistanceMeters": 420, "totalTimeSeconds": 330, "maxCongestion": 1.4 }] }
```

`x/y` 是底图比例坐标（0–1），前端按容器尺寸换算，不引入地图 SDK。

## 7. 测试接缝

| 接缝 | 用例 |
|---|---|
| `RoutePlanner`（纯函数） | 已知小图最短路、绕行方案、权重切换后结果变化、禁边后走次优 |
| `RouteTrafficRecorder` | 记录后权重上升、超过窗口后回落 |
| HTTP | `路线_正常_返回分段`、`路线_不连通_404`、`地点无节点_4001`、`帖子路线_按排序串联` |
