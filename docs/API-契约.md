# API 契约 v1

> 版本：v1（`/api/v1`）。阶段：**P0** 首版必做，**P1** 内容闭环，**P2** 空间能力。
> 契约先于实现：改接口先改本文件，再改代码。

---

## 0. 通用约定

- Base URL：`http://<host>:8080/api/v1`
- 响应体：`{ "code": 0, "message": "ok", "data": ... }`
- 时间格式：`yyyy-MM-dd HH:mm:ss`（Asia/Shanghai）
- 分页入参：`page`（从 1 开始，默认 1）、`pageSize`（默认 10，**超过 50 截断为 50，不报错**）
- 分页出参：`{ "total": 123, "page": 1, "pageSize": 10, "list": [...] }`
- 错误写法统一为 `业务码(HTTP码)`，如 `4001(400)`；错误码表见《代码级设计基线》§3。

### 鉴权三态

| 标记 | 含义 |
|---|---|
| `—` | 匿名，不解析 token |
| `○` | **可选登录**：带 token 且有效则注入用户；无 token 或 token 失效按匿名处理，不返回 401 |
| `✓` | 必须登录，否则 401 |

`○` 接口在匿名时，`my*` 类字段返回 `null`（不是 `false`）。

### 常见错误（各接口只列差异项）

`4001` 参数错误 · `401` 未登录/失效 · `403` 无权限 · `404` 不存在 · `409` 冲突 · `429` 过频 · `500` 服务异常。

## 1. 认证（P0）

| 方法 | 路径 | 鉴权 | 入参 | 出参 | 错误 |
|---|---|---|---|---|---|
| POST | `/auth/register` | — | `{username(3-32), password(6-32), nickname(1-32)}` | `{token, user}` | `4001`, `409` |
| POST | `/auth/login` | — | `{username, password}` | `{token, user}` | `4001`, `401`, `429` |
| POST | `/auth/logout` | ✓ | — | `null` | `401` |
| GET | `/auth/me` | ✓ | — | `user` | `401` |

```json
// user
{ "id": 1, "username": "lin", "nickname": "林知远", "avatar": null,
  "bio": null, "role": "USER", "postCount": 3,
  "followerCount": 0, "followingCount": 2, "createdTime": "2026-11-18 14:00:00" }
```

规则：

- 注册成功直接发 token；
- 用户名唯一靠 DB 唯一索引，并发重复 → `409`；
- 登录失败统一 `401`「用户名或密码错误」，不区分账号是否存在；`status=0` 同样 `401`；
- token 有效期 7 天，Redis 白名单同步过期；登出即失效。

## 2. 用户（P1）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/users/{id}` | ○ | 公开主页，含 `isFollowed`（匿名 null） |
| PUT | `/users/me` | ✓ | `{nickname?, avatar?, bio?}` |
| GET | `/users/{id}/posts` | ○ | 本人可见 `DRAFT/PENDING`，他人只见 `ONLINE` |
| POST | `/users/{id}/follow` | ✓ | 重复关注 → `200`（幂等）；关注自己 → `4001`；目标不存在 → `404` |
| DELETE | `/users/{id}/follow` | ✓ | 未关注时也返回 `200`（幂等） |
| GET | `/users/me/collections` | ✓ | 我的收藏 |
| GET | `/users/me/followed-topics` | ✓ | P2：我关注的地点 |

## 3. 主题 / 地点（P0 基础，P2 补路线）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/topics` | — | `type=PLACE\|CATEGORY`、`category`、`q`、`sort=heat\|rating\|postCount` |
| GET | `/topics/suggest` | — | `q`、`limit≤10` → `[{id,name,type,category,nodeId}]`（发布页选地点） |
| GET | `/topics/{id}` | ○ | 详情；`enabled=0` → `404` |
| GET | `/topics/{id}/posts` | — | 内容流，`sort=latest\|hot` |
| GET | `/topics/{id}/ratings` | ○ | 评分列表分页，含 `myScore` |
| GET | `/topics/{id}/rating-summary` | ○ | `{avg, displayAvg, count, myScore}` |
| PUT | `/topics/{id}/ratings` | ✓ | `{score: 1..5}`；重复提交即改分（upsert）；对 `CATEGORY` → `4001` |
| GET | `/topics/{id}/nearby` | — | P2：`limit≤5` → 距离最近的地点 |

```json
// topic
{ "id": 113, "type": "PLACE", "name": "学生食堂", "category": "食堂",
  "cover": null, "description": null, "heat": 0, "nodeId": 11247,
  "postCount": 12, "todayRouteCount": 74,
  "rating": { "avg": 4.1, "displayAvg": 3.9, "count": 9 } }
```

`avg` 是裸均值，`displayAvg` 是贝叶斯平滑值（先验 10 条 3.5 分），排序与展示用后者。

## 4. 帖子（P0 基础，P1 补互动）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| POST | `/posts` | ✓ | 发帖，可传 `status=DRAFT`，默认 `PENDING` |
| POST | `/files/images` | ✓ | 图片上传（multipart），返回 `{url,width,height}` |
| GET | `/posts/{id}` | ○ | 详情；非 `ONLINE` 仅作者/管理员可见，否则 `404` |
| PUT | `/posts/{id}` | ✓ | 编辑；非作者 `403`；编辑后回 `PENDING`（受 `roam.post.auto-approve` 控制） |
| DELETE | `/posts/{id}` | ✓ | 作者或管理员，软删 |
| GET | `/posts` | ○ | `feed=recommend\|latest\|following`；`following` 未登录 → `401` |
| GET | `/users/me/drafts` | ✓ | 我的草稿 |
| PUT | `/posts/{id}/publish` | ✓ | 草稿发布 |
| GET | `/posts/{id}/comments` | — | P1 评论分页 |
| POST | `/posts/{id}/comments` | ✓ | P1 `{content, parentId?}` |
| POST/DELETE | `/posts/{id}/like` | ✓ | P1 幂等：重复点赞/取消不存在均 `200` |
| POST/DELETE | `/posts/{id}/collect` | ✓ | P1 同上 |
| POST | `/posts/{id}/view` | ○ | P1 浏览计数，按 `uid`/`ip` 60 秒去重 |
| GET | `/posts/{id}/related` | — | P1 同类内容，`limit≤3` |
| GET | `/posts/{id}/route` | — | P2 按帖子的地点顺序出路线 |

发帖入参：

```json
{
  "title": "教三五楼最里面那间教室，晚上几乎没人",
  "content": "……",
  "status": "PENDING",
  "topics": [ { "topicId": 113, "sortNo": 0 }, { "topicId": 114, "sortNo": 1 } ],
  "media": [ { "type": "IMAGE", "url": "https://…", "sortNo": 0 } ]
}
```

规则：

- `topics`：**至少 1 个 `type=PLACE`**，总数 ≤9；`sortNo` 只对 PLACE 生效，取 0..n-1 连续；
- 标题 ≤100 字，正文 ≤20000 字，图片 ≤9 张，单张 ≤10MB；P0 只收 `IMAGE`；
- 列表接口不返回 `content`，详情才返回。

```json
// PostVo（列表形态，详情在此基础上加 content）
{ "id": 1, "title": "…", "excerpt": "…", "status": "ONLINE",
  "author": { "id": 1, "nickname": "林知远", "avatar": null, "followed": false },
  "topics": [ { "id": 113, "type": "PLACE", "name": "学生食堂", "sortNo": 0 } ],
  "media": [ { "type": "IMAGE", "url": "https://…", "sortNo": 0 } ],
  "counts": { "like": 28, "collect": 12, "comment": 31, "view": 1200 },
  "myLiked": null, "myCollected": null,
  "createdTime": "2026-11-18 14:00:00", "updatedTime": "2026-11-18 14:00:00" }
```

> `myLiked/myCollected` 在匿名请求下为 `null`。

## 5. 评论（P1）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/comments/{id}/replies` | — | 子评论分页 |
| DELETE | `/comments/{id}` | ✓ | 作者/管理员软删 |

```json
// CommentVo
{ "id": 9, "postId": 1, "content": "……",
  "author": { "id": 2, "nickname": "周予安", "avatar": null },
  "parentId": 0, "topId": 9, "replyCount": 3, "createdTime": "2026-11-18 15:00:00" }
```

## 6. 通知（P1）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/notifications` | ✓ | `type=COMMENT\|REPLY\|LIKE\|FOLLOW\|MENTION\|SYSTEM` |
| GET | `/notifications/unread-count` | ✓ | `{count}` |
| POST | `/notifications/read` | ✓ | `{ids:[…]}`，空数组 → `4001` |
| PUT | `/notifications/read-all` | ✓ | 全部已读 |

```json
// NotificationVo
{ "id": 5, "type": "COMMENT", "read": false,
  "actor": { "id": 2, "nickname": "周予安", "avatar": null },
  "source": { "type": "POST", "id": 1, "postId": 1 },
  "content": "评论了你的帖子", "createdTime": "2026-11-18 15:00:00" }
```

## 7. 搜索（P1）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/search` | — | `q`(必填,≤50)、`topicId`、`page` → `{posts:{total,list}, topics:{total,list}}` |
| GET | `/search/suggest` | — | 关键词联想，≤10 条 |

- `q` 为空或全空白 → `4001`；
- ES 词级召回优先，ES 不可用降级 MySQL LIKE；
- **相关性只影响排序，不做硬门槛**，没有高相关也照常返回；
- `PostSearchVo` 在 PostVo 基础上加 `highlight: {title, content}`。

## 8. 导航 / 空间（P2）

| 方法 | 路径 | 鉴权 | 说明 |
|---|---|---|---|
| GET | `/nav/route` | — | `fromNodeId`、`toNodeId`、`mode=walk\|bike\|mixed`（默认 walk） |
| GET | `/nav/routes` | — | `points=1,2,3`（有序）、`mode` → 多段路线 |
| GET | `/nav/nodes` | — | 节点列表，只回 `enabled=1` |
| GET | `/nav/edges` | — | 边列表（调试，含 `congestionFactor`） |
| GET | `/posts/{id}/route` | — | 帖子地点顺序 → 等价于 `/nav/routes?points=` |

```json
{ "totalDistance": 480, "totalTime": 360, "mode": "walk",
  "segments": [
    { "from": { "nodeId": 11099, "x": 0.17, "y": 0.31 },
      "to": { "nodeId": 11063, "x": 0.18, "y": 0.24 },
      "distance": 34, "time": 27, "congestion": 1.0, "level": "SMOOTH" }
  ],
  "alternatives": [ { "label": "绕操场", "totalDistance": 520, "totalTime": 420,
                      "congestionSummary": { "smooth": 4, "slow": 1, "jam": 0 },
                      "segments": [] } ] }
```

节点不存在 → `404`；数据缺失不等于系统故障，不返回 `500`。

## 9. 后台（P1）

全部要求 `role=ADMIN`：未登录 `401`，非管理员 `403`。

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/admin/posts` | `status=PENDING\|ONLINE\|OFFLINE`，分页 |
| GET | `/admin/posts/{id}` | 审核详情（含正文、媒体） |
| PUT | `/admin/posts/{id}/status` | `{status, reason?}`；状态机见下 |
| GET | `/admin/users` | 分页，`q`、`status` |
| PUT | `/admin/users/{id}/status` | `{status: 1\|0}` 封禁/解封 |
| GET/POST | `/admin/sensitive-words` | 分页 / 新增 |
| DELETE | `/admin/sensitive-words/{id}` | 删除 |
| GET | `/admin/topics` | 地点数据（只读） |

状态机（非法迁移 → `409`）：

```
PENDING → ONLINE | DELETED
ONLINE  ↔ OFFLINE
OFFLINE → ONLINE | DELETED
```

`post.status` 是唯一权威状态（表里不再有独立的 `deleted` 列）。

## 10. 变更纪律

- 新增/修改接口先改本文件，再写代码；
- 破坏性语义变更升 `v2`，不原地改；
- 每个接口实现时补一条 MockMvc 测试，用例名与本节路径对应；
- 接口变更必须同步界面稿（`docs/mockup.html`），否则视为未完成。
