# 域设计：interaction（点赞 / 收藏）

> 状态与验收见 [实现清单](实现清单.md)；本文只写调用关系、接口与失败语义，不重复状态。

> 范围：帖子点赞、收藏，以及帖子的互动计数与「我是否点过」。
> 不含：关注（user→user）、通知（由 notify 域消费事件）、评论点赞。

## 1. 调用关系

```
InteractionController
 ├─ POST   /posts/{id}/like     → InteractionService.like(postId, uid)
 ├─ DELETE /posts/{id}/like     → InteractionService.unlike(postId, uid)
 ├─ POST   /posts/{id}/collect  → InteractionService.collect(postId, uid)
 └─ DELETE /posts/{id}/collect  → InteractionService.uncollect(postId, uid)

InteractionService
 ├─ PostQueryService.existsOnline(postId)
 ├─ UserFootMapper.insert / delete         唯一键 (user_id, post_id, action)
 └─ 事务提交后发布 PostLikedEvent（供 notify 域）

PostReadService（现有）
 └─ InteractionCountProvider.summarize(postIds, viewerUid)
       ├─ 点赞数、收藏数按 post_id 批量 GROUP BY
       └─ viewer 的 liked / collected 集合（一次查询）
```

`InteractionCountProvider` 声明在 post 域、由 interaction 域实现，避免两个域构造器循环依赖。

## 2. 接口

```java
// api/model/vo
public record InteractionResultVO(boolean active, long count) {}

// service/interaction
public interface InteractionService {
    InteractionResultVO like(long postId, long uid);
    InteractionResultVO unlike(long postId, long uid);
    InteractionResultVO collect(long postId, long uid);
    InteractionResultVO uncollect(long postId, long uid);
}
```

`PostVO.counts` 里的 `like` / `collect` 由本域提供；`myLiked` / `myCollected` 在已登录时给 `true/false`，匿名时保持 `null`。

## 3. 失败语义

| 场景 | 返回 | 说明 |
|---|---|---|
| 未登录 | `401` | 拦截器 |
| 帖子不存在 / 非 ONLINE | `404` | |
| 重复点赞 / 重复收藏 | `200` | **幂等**：唯一键冲突视为成功，不报 409 |
| 取消未点过赞 | `200` | 幂等 |
| 通知发送失败 | 不影响主流程 | 事件在提交后发 |

幂等与计数分开：`insert` 用 `DuplicateKeyException` 判重，只有真正插入成功（`affectedRows = 1`）才发通知，避免连点 5 次发 5 条。

## 4. 数据与事务

`003_interaction.xml`：

```sql
user_foot(
  id, user_id, post_id,
  action VARCHAR(16),          -- LIKE / COLLECT
  created_time,
  UNIQUE KEY uk_user_foot (user_id, post_id, action),
  KEY idx_user_foot_post (post_id, action)
)
```

- 计数实时 `COUNT(*) GROUP BY`，不做 Redis 计数（当前量级不需要，先保证正确）。
- 事件 `PostLikedEvent(postId, uid, action)` 在提交后发布；取消点赞不发事件。

## 5. 测试接缝与用例

| 接缝 | 用例 |
|---|---|
| HTTP | `点赞_成功_返回active和计数`、`点赞_重复_返回200且计数不变`、`取消点赞_未点过_返回200`、`点赞_帖子不存在_404`、`点赞_未登录_401` |
| 读侧 | `帖子详情_登录用户_myLiked为true`、`帖子列表_匿名_myLiked为null` |
| 计数 | `点赞后_列表counts.like加一` |

## 6. 明确不做

- 不把计数写 Redis（P2 数据量上来再说）。
- 不做点赞通知（留给 notify 域消费 `PostLikedEvent`）。
- 不做关注（单独设计）。
