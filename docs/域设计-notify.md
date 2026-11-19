# 域设计：notify（站内通知）

> 范围：评论/回复/点赞/收藏产生站内通知，列表、未读数、已读。
> 不含：MQ 投递（P2）、关注通知、邮件/推送。

## 1. 调用关系

```
CommentWriteService ─┐
InteractionService ──┼─（发布事件）→ NotificationListener
                     │                 ├─ PostQueryService.findAuthorId(postId)
                     │                 ├─ CommentQueryService.findAuthorId(commentId)
                     │                 └─ NotificationService.send(...)
NotificationController
 ├─ GET /notifications              → NotificationService.list(uid, type, page, size)
 ├─ GET /notifications/unread-count → countUnread(uid)
 ├─ POST /notifications/read        → markRead(uid, ids)
 └─ PUT  /notifications/read-all    → markAllRead(uid)
```

## 2. 事件与规则

| 事件 | 收件人 | 类型 |
|---|---|---|
| `CommentCreatedEvent(parentId=null)` | 帖子作者 | `COMMENT` |
| `CommentCreatedEvent(parentId!=null)` | 被回复评论的作者 | `REPLY` |
| `PostLikedEvent(action=LIKE)` | 帖子作者 | `LIKE` |
| `PostLikedEvent(action=COLLECT)` | 帖子作者 | `COLLECT` |

- **自己操作自己的内容不发通知**（`actor == recipient` 直接返回）。
- 收件人不存在或内容已删，静默丢弃并打 info 日志。
- 通知内容只存一句话（"评论了你的帖子"），不存正文快照。

## 3. 接口

```java
public record NotificationVO(Long id, String type, UserBriefVO actor,
                             String sourceType, Long sourceId, Long postId,
                             String content, boolean read, LocalDateTime createdTime) {}

public interface NotificationService {
    void send(long recipientId, long actorId, String type,
              String sourceType, long sourceId, long postId, String content);
    PageResult<NotificationVO> list(long uid, String type, int page, int pageSize);
    long countUnread(long uid);
    void markRead(long uid, Collection<Long> ids);
    void markAllRead(long uid);
}
```

## 4. 失败与一致性

| 场景 | 行为 |
|---|---|
| 自己给自己 | 不发，返回 |
| 收件人等于操作者 | 不发 |
| 重复事件 | 靠 `(recipient, type, source_id, actor)` 唯一键去重，插入冲突时静默成功 |
| 通知写失败 | 不影响评论/点赞主流程：监听器内 try/catch，打 WARN |

**事务边界（对基线的显式例外）**：通知是同一库内的插入，跟着主事务一起提交/回滚，所以用普通 `@EventListener` 同步执行，而不是 `AFTER_COMMIT`。等 P2 换成 MQ 投递时再改成 `AFTER_COMMIT + 发消息`，否则会出现"评论已提交、通知丢失"的窗口。这条记在这里，改的时候一并改测试。

## 5. 数据

`004_notification.xml`：

```sql
notification(
  id, user_id, actor_id, type VARCHAR(16), source_type VARCHAR(16),
  source_id BIGINT, post_id BIGINT,
  content VARCHAR(100), read_flag TINYINT DEFAULT 0,
  created_time,
  UNIQUE KEY uk_notify (user_id, type, source_id, actor_id),
  KEY idx_notify_user (user_id, read_flag, created_time)
)
```

## 6. 测试接缝

| 接缝 | 用例 |
|---|---|
| HTTP | `评论后_作者收到一条通知`、`自己评论自己_不产生通知`、`点赞后_作者收到LIKE`、`未读数正确`、`标记已读后未读数归零`、`重复点赞事件_只留一条通知` |
