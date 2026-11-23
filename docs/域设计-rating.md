# 域设计：rating（地点评分）

> 状态与验收见 [实现清单](实现清单.md)；本文只写调用关系、接口、数据、协调与失败语义。

> 范围：用户对地点打分（1–5 星）、一人一次、均分计算与展示。
> 不含：帖子评分、评论评分、评分文字理由、评分排序榜单。

## 1. 调用关系

```
RatingController
 ├─ PUT /api/v1/topics/{id}/ratings   → RatingService.rate(topicId, uid, score)
 └─ GET /api/v1/topics/{id}/ratings   → RatingService.summary(topicId, viewerUid)

RatingServiceImpl
 ├─ TopicQueryService.findEnabledPlace(topicId)   只允许地点，不允许分类
 ├─ TopicRatingMapper                             唯一键 (user_id, topic_id)
 └─ 贝叶斯均分（现算，不落快照）
```

## 2. 接口签名

```java
public record RatingCommand(@Min(1) @Max(5) int score) {}

public record RatingSummaryVO(long topicId, int count, double average,
                              Integer myScore, int[] distribution) {}   // distribution 为 1..5 星各几条

public interface RatingService {
    RatingSummaryVO rate(long topicId, long uid, int score);   // 新增或改分
    RatingSummaryVO summary(long topicId, Long viewerUid);     // 匿名时 myScore 为 null
}
```

## 3. 数据与字段

```sql
topic_rating(
  id, user_id, topic_id, score TINYINT, created_time, updated_time,
  UNIQUE KEY uk_rating (user_id, topic_id),
  KEY idx_rating_topic (topic_id)
)
```

均分公式（现算，不建快照表）：

```
average = (C * m + sum(score)) / (C + count)
C = 10   先验条数
m = 3.5  先验均值
```

这样 1 条 5 星不会排到 100 条 4.2 星前面；count 为 0 时返回 `average = 3.5, count = 0`，前端显示“暂无评分”。

## 4. 协调

**我从谁拿数据**

- `TopicQueryService`：确认 `topicId` 存在、`type = PLACE`、`enabled = 1`；分类不允许打分。
- 用户身份来自拦截器上下文，不额外查库。

**我给谁提供数据**

- 地点页（topic 详情）：`summary` 里的 `average` 与 `count` 用于头部展示，`distribution` 画五档条形。
- 帖子列表不展示评分——避免每次列表都算一遍均分。

**时序与幂等**

- 同一用户重复打分 = 改分（`ON DUPLICATE KEY UPDATE` 语义），不新增行；返回更新后的 `myScore`。
- 评分不影响 `topic.heat`，热度只由发帖驱动，两者不要混。

**跨域失败**

- topic 不存在或不是地点 → `404` / `4001`，不写库。
- 并发重复提交由唯一键兜底，捕获冲突后按改分处理，不报 500。

## 5. 失败语义

| 场景 | 返回 |
|---|---|
| 未登录打分 | `401` |
| 分数不在 1–5 | `4001` |
| 地点不存在 / 已被禁用 | `404` |
| 对分类打分 | `4001`「只能给地点打分」 |
| 重复打分 | `200`，覆盖旧分 |
| 匿名看评分 | `200`，`myScore` 为 null |

## 6. 实测记录（2026-11-22）

- 教三楼：0 条评分时返回先验 3.5；一人打 5 星后 `(35+5)/11 = 3.636`，详情页显示 3.6。
- 同一人改 1 星：count 仍为 1，均分降到 3.27，说明唯一键生效。
- 地点页点击星星即时更新并保留 `myScore`，刷新后仍显示已打分。

## 7. 测试接缝

| 接缝 | 用例 |
|---|---|
| HTTP | `评分_首次打分_均分按贝叶斯`、`评分_改分_行数不变`、`评分_分类_4001`、`评分_未登录_401` |
| 计算 | `均分_零条_返回先验3.5`、`均分_一条五星_不高于先验权重` |

## 评价流（2026-11-23）

评分从「只打星」扩成「星 + 文字 + 图」的一条评价，形态接近虎扑：

- 表：`topic_rating` 增加 `review_text`、`review_images`（逗号分隔 url），唯一键仍是 `(user_id, topic_id)`，一人一条；
- 写：`PUT /topics/{id}/ratings`，`score` 必填 1–5，`review` ≤200 字，`images` ≤3 且必须 `/files/` 开头；重复提交即修改；
- 读：`GET /topics/{id}/ratings` 返回 `{summary, reviews}`，`summary` 仍是均分/人数/分布/`myScore`，`reviews` 分页返回作者昵称头像、分数、文字、图、时间、`mine`；
- 删：`DELETE` 删除自己的评价，均分与人数按剩余评价回落；
- 均分公式不变：`(10×3.5 + sum) / (10 + count)`。
