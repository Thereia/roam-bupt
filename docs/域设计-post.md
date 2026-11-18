# 域设计：post（含 topic 读侧）

> 状态：待确认。确认后才写代码（见 `AGENTS.md` §开工流程）。
> 范围：发帖、帖子详情、首页信息流、地点页（主题详情 + 该地点内容流）。
> 不含：点赞/收藏/评论（P1）、草稿与发布（P1）、上传接口（P1）、搜索（P1）。

## 1. 调用关系

```
PostController
 ├─ POST /posts            → PostWriteService.create(cmd, uid)
 ├─ GET  /posts/{id}       → PostReadService.detail(id, viewerUid?)
 └─ GET  /posts            → PostReadService.feed(query, viewerUid?)

TopicController
 ├─ GET /topics            → TopicReadService.list(query)
 ├─ GET /topics/{id}       → TopicReadService.detail(id, viewerUid?)
 └─ GET /topics/{id}/posts → PostReadService.byTopic(topicId, sort, page, size)

PostWriteService
 ├─ TopicQueryService.findPlaces(ids)        校验：存在、enabled、type=PLACE
 ├─ PostRepository.insertPost / insertTopics / insertMedia   （只写自己的表）
 └─ ApplicationEventPublisher（AFTER_COMMIT 后发 PostCreatedEvent）

PostReadService
 ├─ PostRepository.findByIds / listByTopic / listFeed
 ├─ UserQueryService.getById                 作者信息
 ├─ TopicQueryService.findByIds               主题信息
 └─ 组装 PostVO

TopicReadService
 ├─ TopicRepository.findById / list
 └─ PostRepository.countByTopic / countTodayRoute（地点页统计）
```

依赖方向保持 `web → service → core → api`；post 不直接读 `topic` 表，topic 不直接读 `post` 表。

## 2. 接口

```java
// api/model/dto
public record PostCreateCommand(String title, String content, String status,
                                List<TopicRef> topics, List<MediaRef> media) {}
public record TopicRef(Long topicId, Integer sortNo) {}
public record MediaRef(String type, String url, Integer sortNo) {}
public record FeedQuery(String feed, Long topicId, int page, int pageSize) {}

// api/model/vo
public record PostVO(Long id, String title, String excerpt, String content, String status,
                     UserBriefVO author, List<TopicBriefVO> topics, List<MediaVO> media,
                     CountsVO counts, Boolean myLiked, Boolean myCollected,
                     LocalDateTime createdTime, LocalDateTime updatedTime) {}
public record TopicVO(Long id, String type, String name, String category, String cover,
                      String description, Integer heat, Long nodeId,
                      long postCount, long todayRouteCount, RatingVO rating) {}

// service/post
public interface PostWriteService {
    PostCreatedVO create(PostCreateCommand command, long uid);
}
public interface PostReadService {
    PostVO detail(long postId, Long viewerUid);
    PageResult<PostVO> feed(FeedQuery query, Long viewerUid);
    PageResult<PostVO> byTopic(long topicId, String sort, int page, int pageSize);
}

// service/topic
public interface TopicReadService {
    TopicVO detail(long topicId, Long viewerUid);
    PageResult<TopicVO> list(TopicQuery query);
}
```

字段命名与 `docs/API-契约.md` 保持一致；列表不返回 `content`，详情才返回。

## 3. 失败语义

| 场景 | 返回 | 说明 |
|---|---|---|
| 未登录发帖 | `401` | 拦截器负责 |
| `topics` 为空，或不含 `type=PLACE` | `4001`「至少挂一个地点」 | 只挂分类不能发 |
| 主题数量 > 9 / 媒体 > 9 | `4001` | |
| 同一帖子重复挂同一主题 | `4001` | 插入前按 id 去重，不靠唯一键报 409 |
| 主题不存在或 `enabled=0` | `4001`「主题不可用」 | 不泄露隐藏主题是否存在 |
| 标题 > 100 字 / 正文 > 20000 字 / 正文 > 48KB | `4001` | 字节数在 service 再校验一次 |
| 图片 URL 不是本站前缀 | `4001` | P1 上传接口上线后生效 |
| 帖子不存在、已删、非 ONLINE 且非作者/管理员 | `404` | 不区分「不存在」和「无权限」，避免探测 |
| 主题不存在（详情页） | `404` | |
| 分页参数非法 | 截断，不报错 | `pageSize > 50` 取 50 |

## 4. 数据与事务

| 位置 | 内容 |
|---|---|
| `post` | 一条帖子；`status` 默认 `PENDING`（配置 `roam.post.auto-approve=true` 时直接 `ONLINE`） |
| `post_topic` | 主题关联；**只为 PLACE 写 `sort_no`**，归一化成 `0..n-1` 连续 |
| `post_media` | 媒体；P0 只收 `IMAGE` |
| 事件 | `PostCreatedEvent(postId, uid)`，`AFTER_COMMIT` 发布（P1 接搜索索引） |

事务：三个 insert 在同一个 `@Transactional` 方法里；事件在提交后由监听器处理，写进代码前先确认没有同类自调用。

## 5. 测试接缝与首批用例

| 接缝 | 装配 | 用例 |
|---|---|---|
| HTTP | `@SpringBootTest` + MockMvc + 真 MySQL | `发帖_成功_返回id与PENDING`、`发帖_只挂分类_返回4001`、`发帖_未登录_返回401`、`详情_不存在_返回404`、`详情_匿名可见_ONLINE帖子` |
| Service | 同上 | `发帖_媒体插入失败_帖子不存在`（事务回滚）、`发帖_主题排序_归一化为0到n-1` |
| 读侧 | 同上 | `地点页_返回帖子数与分类信息` |

## 6. 明确不做

点赞、收藏、评论、草稿、发布、上传、搜索、浏览量。这些到对应阶段再进本文件。
