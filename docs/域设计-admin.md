# 域设计：admin（后台与治理）

> 状态与验收见 [实现清单](实现清单.md)；本文只写调用关系、接口与失败语义，不重复状态。

> 范围：管理员权限、帖子审核状态机、用户封禁、敏感词。
> 不含：数据统计、操作日志、角色管理（只有 USER / ADMIN 两种）。

## 1. 调用关系

```
AuthInterceptor
 └─ 读到 @Permission(ADMIN) 且当前用户 role != ADMIN → 403

AdminPostController
 ├─ GET  /admin/posts?status=PENDING        → PostAdminService.listByStatus
 ├─ PUT  /admin/posts/{id}/status           → PostAdminService.changeStatus(id, status)
 └─ GET  /admin/posts/{id}                  → PostReadService.detail（管理员可见非 ONLINE）

AdminUserController
 ├─ GET  /admin/users?q=&page               → UserAdminService.page
 └─ PUT  /admin/users/{id}/status           → UserAdminService.changeStatus

AdminSensitiveWordController
 ├─ GET/POST/DELETE /admin/sensitive-words  → SensitiveWordAdminService

PostWriteValidator / CommentWriteService
 └─ SensitiveWordFilter.check(content) → 命中则 4001「内容包含敏感词」

PostAdminService
 ├─ PostMapper.updateStatus
 └─（提交后）PostStatusChangedEvent → SearchIndexListener 同步索引
```

## 2. 权限

- 注解 `@Permission("ADMIN")` 放在 Controller 方法或类上；
- 拦截器在写入上下文后检查：命中注解且 `role != ADMIN` 直接 403，不进 Controller；
- 不在 Service 里散落 `if (admin)` 判断。

## 3. 状态机

```
PENDING  → ONLINE | DELETED
ONLINE   → OFFLINE | DELETED
OFFLINE  → ONLINE | DELETED
DELETED  → 终止
```

非法迁移返回 `409`。状态变化后同步搜索索引（下线即从索引去掉）。

## 4. 失败语义

| 场景 | 返回 |
|---|---|
| 非管理员访问 `/admin/**` | `403` |
| 目标帖子不存在 | `404` |
| 非法状态迁移（如 DELETED → ONLINE） | `409` |
| 封禁自己 | `4001` |
| 封禁后，旧 token 立即失效 | 下次请求 401（`revokeAllOf`） |
| 内容命中敏感词 | `4001`「内容包含敏感词」 |
| 敏感词为空或重复 | `4001` / `409` |

## 5. 数据

`006_sensitive_word.xml`：

```sql
sensitive_word(
  id, word VARCHAR(64) UNIQUE, enabled TINYINT, created_time
)
```

词表加载到内存（启动时 + 变更时刷新），匹配用简单的包含判断；P2 再换 DFA / 词库方案。

## 6. 测试接缝

| 接缝 | 用例 |
|---|---|
| 权限 | `后台_普通用户_403`、`后台_管理员_200` |
| 审核 | `审核_PENDING转ONLINE_首页可见`、`审核_非法迁移_409`、`审核_DELETED后详情404` |
| 封禁 | `封禁_旧token失效_401`、`封禁自己_4001` |
| 敏感词 | `发帖含敏感词_4001`、`评论含敏感词_4001` |

## 协调（谁给我数据 / 我给谁数据）

- **拿**：拦截器提供当前用户角色（每请求查库，降权与封禁立即生效）。
- **给**：审核改状态后发 `PostStatusChangedEvent` → search 同步索引；封禁调 `SessionStore.revokeAllOf` 让旧 token 立即失效。
- **敏感词**：接口在 core（`SensitiveWordFilter`），实现在 moderation；post / comment 写前调用，命中即 4001。
- **时序**：先改状态再发事件；事件消费失败只打 WARN，不回滚状态。
