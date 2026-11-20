# 域设计：space（路线与拥挤度）

> 状态与验收见 [实现清单](实现清单.md)；本文只写调用关系、接口与失败语义，不重复状态。

> 范围：路网数据、寻路算法、多地点串联、拥挤度记录与衰减、路线接口。
> 不含：逐转向导航、实时 GPS、底图配准、室内楼层。

## 1. 调用关系

```
RouteController
 ├─ POST /api/v1/nav/route        → SpaceService.plan(stops, mode)
 ├─ POST /api/v1/nav/routes       → SpaceService.plans(stops, mode, alternatives)
 └─ GET  /posts/{id}/route        → PostRouteService.routeOfPost(postId)   （按 post_topic.sort_no 串段）

RoutePlanner（纯函数，无 Spring 依赖）
 ├─ 输入：RouteGraph（节点 + 边） + List<RouteStop> + TravelMode(DISTANCE/TIME/MIXED)
 ├─ 输出：RoutePlan（分段列表、总距离、总耗时、每段拥挤系数）
 └─ 不读数据库、不读 Redis、不写任何东西 → 单测直接喂图

RouteGraphProvider
 └─ 从 route_node / route_edge 载入图，进程内缓存，`enabled = 1` 才进图

RouteTrafficRecorder
 ├─ 寻路成功 → 对路径上的边累加 Redis 分钟桶
 └─ 读取时按时间衰减，算漏桶系数 1 + K·u/(1-u)
```

`RoutePlanner` 是 `space` 域的深模块：签名固定为 `RoutePlan plan(List<RouteStop> stops, TravelMode mode)`，图与拥挤度都从外面注入，测试不需要起 Spring。

## 2. 数据

- `route_node`：`node_id, name, type, x_ratio, y_ratio, building_id, floor, poi_category, enabled`
- `route_edge`：`edge_id, from_node_id, to_node_id, distance_meters, time_seconds, edge_type, bike_allowed, congestion_factor, enabled`
- 静态数据由 `scripts/` 导出的 CSV 灌入（见 `docs/数据导出说明.md`），**不建 route 表**，路线每次现算。
- `topic.node_id` 是地点到图入口节点的映射；没有 node_id 的地点不参与寻路。

## 3. 算法

1. **单段**：Dijkstra，三种权重（距离 / 时间 / 混合 = 时间 × 拥挤系数）。
2. **多方案**：换权重 + 逐条禁用已选路径中的关键边重算，产出 2–3 条；结果按总耗时排序并去重（路径相同则合并）。
3. **多地点**：按 `post_topic.sort_no` 顺序串行分段；用户没排序时用 DP 求近似最优顺序（点数 ≤ 6 时全排列，超过则最近邻 + 2-opt）。
4. **拥挤度**：`1 + K·u/(1-u)`，`u` 为窗口内该边被请求次数归一化；`K` 与窗口长写在配置里，窗口默认 5 分钟，TTL 自动过期。

## 4. 失败语义

| 场景 | 返回 |
|---|---|
| 起点或终点缺少 `node_id` | `4001`「该地点暂不支持路线」 |
| 起终点不连通 | `404`「两点之间没有可走的路」 |
| `stops` 为空或超过 6 个 | `4001` |
| 帖子没有地点主题 | `4001`「这条帖子没有地点」 |
| Redis 不可用 | 拥挤度按 0 处理，路线照常返回（降级不报错） |
| 图数据为空 | `503`「路网数据未就绪」，并打 ERROR |

## 5. 与其它域的边界

- 只依赖 `topic.node_id` 与 `post_topic.sort_no`，**不回写** post 域；
- 帖子路线不落库、不缓存到帖子表；短 TTL 缓存放本域自己的 key，键含起终点与 mode。

## 6. 测试接缝

| 接缝 | 用例 |
|---|---|
| `RoutePlanner`（纯函数） | 已知小图最短路、绕行方案、权重切换后结果变化、禁边后改走次优 |
| `RouteTrafficRecorder` | 记录后权重上升、静置超过窗口后回落 |
| HTTP | `路线_正常_返回分段与总耗时`、`路线_不连通_404`、`路线_地点无节点_4001`、`帖子路线_按排序串联` |
