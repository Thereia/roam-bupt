# 域设计：search

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
