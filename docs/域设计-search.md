# 域设计：search

> 状态与验收见 [实现清单](实现清单.md)；本文只写调用关系、接口与失败语义，不重复状态。

> 范围：帖子搜索（关键词 + 可选地点过滤），ES 召回 + MySQL 兜底。
> 不含：向量/语义召回（P2）、搜索联想、搜索热词统计。

## 1. 调用关系

```
SearchController
 └─ GET /search?q=&topicId=&page= → SearchService.search(query, viewerUid)

SearchServiceImpl
 ├─ EsSearchService.search(...)        ES 可用时走这里
 ├─ MysqlSearchService.search(...)     ES 关闭或抛异常时兜底
 └─ PostReadService.byIds(ids, viewerUid)   只拿 id，展示回读 MySQL

SearchIndexListener
 ├─ PostCreatedEvent → SearchIndexService.index(postId)
 └─（P2 帖子编辑/删除事件 → update / delete）

SearchIndexService
 ├─ ensureIndex()     启动时创建 mapping（已存在则跳过）
 └─ index(postId)     从 MySQL 读帖子，写一条文档
```

**ES 只负责召回与排序，返回给前端的字段一律回读 MySQL**——避免 ES 与库两份数据不一致，这是 taco/paicoding 里踩过的坑。

## 2. 索引

索引名 `roam_post_v1`：

```json
{
  "postId": 1, "title": "...", "content": "...",
  "authorId": 1, "authorName": "林知远",
  "topicIds": [79, 1002], "topicNames": ["教三楼", "自习"],
  "status": "ONLINE", "createdTime": "2026-11-19T10:00:00"
}
```

- `title` / `content`：`text`，用标准分词器；中文按字切分能保证"教三""食堂"这类关键词可召回。
- `topicIds`：`long`，用于地点过滤。
- `status`：`keyword`，只索引可公开的帖子。
- 建索引时显式写 mapping，不依赖动态映射。

## 3. 接口与降级

```java
public record SearchQuery(String keyword, Long topicId, int page, int pageSize) {}

public interface SearchService {
    PageResult<PostVO> search(SearchQuery query, Long viewerUid);
}
```

| 场景 | 行为 |
|---|---|
| ES 正常 | ES 召回 id 与总数 → 回读 MySQL 组装 |
| ES 关闭（`roam.search.enabled=false`） | 直接走 MySQL LIKE |
| ES 抛异常 / 超时 | catch → 打 WARN + 走 MySQL LIKE，响应仍是 200 |
| 关键词为空或全空白 | `4001` |
| 关键词超过 50 字 | `4001` |
| 无结果 | 返回空列表，**不返回错误**；不做"没有找到"的推荐区 |

排序：ES 用 `_score` + `createdTime` 次级排序；MySQL 兜底按 `created_time DESC`。**相关性只影响排序，不做硬门槛**（与用户既有偏好一致）。

## 4. 配置

```yaml
roam:
  search:
    enabled: true
    host: 192.168.153.129
    port: 9200
    index: roam_post_v1
    connect-timeout-ms: 1000
    socket-timeout-ms: 2000
```

ES 不可用不能拖住请求：连接 1s、读取 2s，超时即降级。

## 5. 数据同步

- 帖子创建后同步索引（`PostCreatedEvent`，`@TransactionalEventListener(AFTER_COMMIT)`，失败只打 WARN，不重试）。
- 启动时如果 `enabled=true`，异步做一次全量重建（幂等，按 postId upsert）。
- **不做**双写事务：ES 挂了不影响发帖。

## 6. 测试接缝

| 接缝 | 用例 |
|---|---|
| MySQL 兜底 | `搜索_关闭ES_按标题命中`、`搜索_关键词为空_4001`、`搜索_带地点过滤_只返回该地点帖子` |
| ES 路径 | 手工 e2e：写入索引后能搜到、停掉 ES 仍返回结果（降级） |

CI 里只跑兜底路径（`roam.search.enabled=false`），ES 路径靠本地联调验证——不把 ES 变成单测的硬依赖。

## 7. 明确不做

向量召回、搜索联想、热词统计、同义词、拼音搜索。

## 协调（谁给我数据 / 我给谁数据）

- **监听**：`PostCreatedEvent` 建索引、`PostStatusChangedEvent` 更新或删除（都走 AFTER_COMMIT）。
- **拿**：post 域读标题正文，post_topic 读地点 id；不写任何业务表。
- **给**：只给帖子 id 与总数，展示字段一律回读 MySQL（避免两份数据不一致）。
- **时序**：索引失败只打 WARN，不影响发帖；启动时全量重建兜底。

## 索引同步与高亮（2026-11-23）

- **内容编辑**：`PostWriteServiceImpl.update` 发 `PostUpdatedEvent`，监听器整篇重建索引（标题/正文/地点一起更新）；
- **状态变化**：`PostStatusChangedEvent` 只改文档 `status`（`_update`），**非 ONLINE（下线/删除/待审）直接删文档**，索引里只保留公开内容；
- **降级**：ES 异常或命中 0 条都回退 MySQL `LIKE`；
- **高亮**：前端按已提交的关键词做字面高亮（`HighlightText`），不依赖 ES highlight 字段；ES 侧只召回 id。

## 语义召回与融合（2026-11-23）

与 taco 同一条线：ES 里同时放 BM25 文本和向量，检索时双路召回后融合。

- **向量化**：智谱 `embedding-3`，1024 维；只在 `roam.search.vector.enabled=true` 且配置了 `ROAM_EMBEDDING_API_KEY` 时启用；
- **索引**：`embedding` 字段为 `dense_vector`（dims=1024、cosine）；建帖/编辑时把「标题 + 正文」向量化一并写入；向量化失败只记 warn，BM25 照常可用；
- **检索**（分页口径照 taco）：
  - 两路都从第 1 名取到“本页末尾”：`need = min(fusionWindow, offset+size)`，窗口默认 500；
  - 融合用加权 RRF：关键词 1.0、语义 0.7（`semantic-weight`，靠评测定，不拍脑袋）；
  - 关键词命中时语义只做候选集内重排，避免整库灌入结果；关键词零命中时语义兜底取前 12 条；
  - `total` 用关键词腿的真实命中数，不是融合列表长度；
  - 翻页超出融合窗口时退化成纯 BM25 的同 offset 页，不返回空页；
  - ES 异常、向量服务异常：逐级降级到 BM25 → MySQL `LIKE`；
- **平台约束**：ES 8.15 的 RRF retriever 需要 license 覆盖，本机集群返回 403，所以走应用层融合；
  将来 license 可用或语料再上一个量级，可切到引擎层 RRF（一个请求内两路 retriever）。

配置项（`roam.search.vector`）：`enabled`、`endpoint`、`api-key`、`model`、`dims`、`timeout-ms`、`top-k`。
