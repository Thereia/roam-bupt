# 域设计：user（个人主页 + 关注）

> 范围：公开主页、用户帖子列表、关注/取关、关注流。
> 不含：资料编辑、粉丝列表页、@ 提及。

## 1. 调用关系

```
UserController
 ├─ GET    /users/{id}          → UserProfileService.profile(id, viewerUid)
 ├─ GET    /users/{id}/posts    → PostReadService.byUser(id, viewerUid, page, size)
 ├─ POST   /users/{id}/follow   → FollowService.follow(viewerUid, id)
 └─ DELETE /users/{id}/follow   → FollowService.unfollow(viewerUid, id)

UserProfileService
 ├─ UserQueryService.getById
 ├─ FollowService.isFollowed / countFollowers / countFollowing
 └─ PostQueryService.countByUser

PostReadService.feed(following)
 └─ UserQueryService.findFollowingIds(uid) → PostMapper.pageByUserIds
```

关注数据属于 user 域，post 域只通过 `UserQueryService` 拿"我关注了谁"，不直接读 follow 表。

## 2. 接口

```java
public record UserProfileVO(Long id, String username, String nickname, String avatar, String bio,
                            long postCount, long followerCount, long followingCount,
                            Boolean isFollowed) {}

public record FollowResultVO(boolean followed, long followerCount) {}

public interface FollowService {
    FollowResultVO follow(long uid, long targetUid);
    FollowResultVO unfollow(long uid, long targetUid);
    boolean isFollowed(long uid, long targetUid);
    long countFollowers(long uid);
    long countFollowing(long uid);
}
```

## 3. 失败语义

| 场景 | 返回 |
|---|---|
| 目标用户不存在或已禁用 | `404` |
| 关注自己 | `4001` |
| 重复关注 | `200`（幂等，计数不变） |
| 取消未关注的用户 | `200`（幂等） |
| 未登录关注 | `401` |
| 匿名看主页 | `200`，`isFollowed` 为 `null` |
| 看别人的帖子 | 只返回 `ONLINE` |
| 看自己的帖子 | 包含 `PENDING` / `DRAFT` |

## 4. 数据

`005_follow.xml`：

```sql
follow(
  id, user_id, target_user_id, created_time,
  UNIQUE KEY uk_follow (user_id, target_user_id),
  KEY idx_follow_target (target_user_id)
)
```

计数实时 `COUNT(*)`；帖子数只算 `ONLINE`。

## 5. 测试接缝

| 接缝 | 用例 |
|---|---|
| HTTP | `主页_匿名_返回计数且isFollowed为空`、`关注_成功_粉丝数加一`、`关注_重复_计数不变`、`关注_自己_4001`、`关注_目标不存在_404`、`取关_未关注_200`、`关注流_只出现关注者的帖子`、`主页_本人可见PENDING_他人不可见` |
