# 域设计：protection（限流与代理信任）

> 状态与验收见 [实现清单](实现清单.md)；本文只写调用关系、接口、协调与失败语义。

> 范围：写操作限流、客户端 IP 解析（X-Forwarded-For 信任边界）。
> 不含：分布式限流（多实例时换 Redis 实现）、WAF、CC 防护、验证码。

## 1. 调用关系

```
WebConfig
 ├─ AuthInterceptor（先）      解析 token → ReqInfo
 └─ WriteRateLimitInterceptor（后，按 uid 计数）

WriteRateLimitInterceptor
 ├─ WriteRateLimiter.tryAcquire(key)      key = u:{uid} 或 ip:{clientIp}
 └─ ClientIpResolver.resolve(request)     未登录时取 IP

ClientIpResolver
 └─ SecurityProperties.trustForwardedFor / trustedProxies

AuthController
 └─ ClientIpResolver.resolve()            登录限流用同一个来源
```

顺序很关键：限流拦截器必须排在鉴权之后，否则拿不到 uid，所有请求都会退化成按 IP 计数。

## 2. 接口

```java
public interface WriteRateLimiter {
    boolean tryAcquire(String key);
}

public record SecurityProperties(boolean trustForwardedFor,
                                 List<String> trustedProxies,
                                 int writePerMinute) {}

public class ClientIpResolver {
    public String resolve(HttpServletRequest request);
}
```

## 3. 规则

**写操作限流**

- 只拦 `POST / PUT / DELETE`；`GET` 不计数——读多写少的场景里，限读没有意义。
- 已登录按 `u:{uid}`，未登录按 `ip:{ip}`。
- 登录/注册（`/api/v1/auth/**`）跳过，它们有自己的「IP + 用户名」限流。
- 窗口 1 分钟，默认 30 次（`roam.security.write-per-minute`）。

**XFF 信任边界**

| 配置 | 行为 |
|---|---|
| `trust-forwarded-for=false`（默认） | 一律用 `request.getRemoteAddr()`，XFF 头直接忽略 |
| `=true` 且 `trusted-proxies` 为空 | 采信 XFF 最左侧地址 |
| `=true` 且配置了代理列表 | 只有 `remoteAddr` 在列表里才采信 XFF，否则仍用 remoteAddr |

为什么默认不信：`X-Forwarded-For` 是客户端可自己写的请求头。无条件采信等于把限流键交给攻击者——换个头就换一个桶。

## 4. 失败语义

| 场景 | 返回 |
|---|---|
| 超过写操作阈值 | `429`，`{"code":429,"message":"操作太频繁，请稍后再试"}` |
| 登录/注册超阈值 | `429`（由登录限流负责，与本拦截器无关） |
| 读请求 | 不受影响 |
| Redis 不可用 | 与限流无关（内存实现）；换 Redis 实现后按失败关闭处理 |

## 5. 协调

- **与 auth 域**：登录限流复用 `ClientIpResolver`，保证“同一个客户端”在两处定义一致。
- **与 post/comment/storage/rating**：这些写接口不需要各自实现限流，统一由拦截器兜住。
- **与部署**：只有应用跑在 nginx / 网关后面时才应打开 `trust-forwarded-for`，并把代理地址写进 `trusted-proxies`。

## 6. 测试接缝

| 接缝 | 用例 |
|---|---|
| `ClientIpResolver`（单测） | 默认忽略 XFF；开启后取最左；来源不可信时忽略；来源可信时采信 |
| HTTP | `写操作超过阈值_返回429`、`读操作不受影响` |
