# 域设计：comment

> 状态：已按用户「开始吧」执行，代码与本文同步提交。
> 范围：评论的发表、列表、回复列表、删除，以及帖子评论数。
> 不含：敏感词与审核（P2）、@ 通知（随 notify 域）、点赞。

## 1. 调用关系

```
CommentController
 ├─ POST   /posts/{id}/comments      → CommentWriteService.create(postId, command, uid)
 ├─ GET    /posts/{id}/comments      → CommentReadService.listTop(postId, page, size)
 ├─ GET    /comments/{id}/replies    → CommentReadService.listReplies(commentId, page, size)
 └─ DELETE /comments/{id}            → CommentWriteService.delete(commentId, uid, role)

CommentWriteService
 ├─ PostQueryService.existsOnline(postId)      帖子必须存在且在线
 ├─ CommentMapper.insert                     只写自己的表
 └─（事务提交后）发布 CommentCreatedEvent，供 notify 域消费

CommentReadService
 ├─ CommentMapper.listTop / countTop / listReplies
 ├─ UserQueryService.getById                  作者信息
 └─ 组装 CommentVO

PostReadService（现有）
 ├─ CommentCountProvider.countByPost(postId)  评论数
 └─ CommentCountProvider.countByPosts(ids)    列表页批量，避免 N+1
```

`CommentCountProvider` 接口声明在 post 域、由 comment 域实现，避免 `post ↔ comment` 构造器循环依赖（与 `PostCountProvider` 同法）。

## 2. 接口

```java
// api/model/dto
public record CommentCreateCommand(@NotBlank @Size(max = 1000) String content, Long parentId) {}

// api/model/vo
public record CommentVO(Long id, Long postId, String content, UserBriefVO author,
                        Long parentId, Long topId, int replyCount, LocalDateTime createdTime) {}

// service/comment
public interface CommentWriteService {
    CommentCreatedVO create(long postId, CommentCreateCommand command, long uid);
    void delete(long commentId, long uid, String role);
}
public interface CommentReadService {
    PageResult<CommentVO> listTop(long postId, int page, int pageSize);
    PageResult<CommentVO> listReplies(long topCommentId, int page, int pageSize);
}
```

## 3. 失败语义

| 场景 | 返回 | 说明 |
|---|---|---|
| 未登录发评论 | `401` | 拦截器 |
| 帖子不存在 / 非 ONLINE | `404` | 与帖子详情同一口径，不泄露草稿 |
| 正文为空 / 超 1000 字 | `4001` | |
| `parentId` 指向不存在的评论 | `4001`「回复的评论不存在」 | |
| `parentId` 属于别的帖子 | `4001` | 防止跨帖串评论 |
| 删除非本人评论 | `403` | 管理员例外 |
| 删除不存在的评论 | `404` | |
| 重复删除 | `404` | 已删即不存在 |

两级结构：`parentId = 0` 为一级评论（`topId = 0`）；回复的 `parentId` 为一级评论 id，`topId` 同为一层评论 id。不做无限层级。

## 4. 数据与事务

`002_comment.xml`：

```sql
comment(
  id, post_id, user_id, content VARCHAR(1000),
  parent_id DEFAULT 0, top_id DEFAULT 0,
  reply_count INT DEFAULT 0,          -- 一级评论的回复数，冗余字段
  deleted TINYINT DEFAULT 0,
  created_time,
  KEY idx_comment_post (post_id, top_id, created_time),
  KEY idx_comment_top (top_id, created_time),
  KEY idx_comment_user (user_id)
)
```

- 删除用 `deleted = 1`，**这张表只有这一个删除真相**，不引入 status。
- 发表回复时对一级评论 `reply_count + 1`，同一个事务内完成。
- 事件 `CommentCreatedEvent(postId, commentId, uid, parentId)` 同步发布；需要 AFTER_COMMIT 的消费方自己加注解（与点赞事件一致）。

## 5. 测试接缝与用例

| 接缝 | 用例 |
|---|---|
| HTTP | `评论_登录后发表_返回id`、`评论_未登录_401`、`评论_帖子不存在_404`、`评论_超长_4001`、`评论列表_匿名可见`、`删除_他人评论_403` |
| Service | `回复_父评论属于别的帖子_4001`、`回复_一级评论replyCount加一` |
| 计数 | `帖子详情_评论数等于一级评论数` |

## 6. 明确不做

敏感词过滤、审核、@ 提及、点赞评论。敏感词与后台审核一起放 P2。
