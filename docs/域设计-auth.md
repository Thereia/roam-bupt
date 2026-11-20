# 域设计：auth

> 状态与验收见 [实现清单](实现清单.md)；本文只写调用关系、接口与失败语义，不重复状态。

> 范围：注册、登录、登出、当前用户、鉴权拦截。不含刷新令牌、改密、验证码、设备管理。

## 1. 调用关系

```
AuthController
 ├─ POST /auth/register → AuthService.register(cmd, clientIp)
 ├─ POST /auth/login    → AuthService.login(cmd, clientIp)
 ├─ POST /auth/logout   → AuthService.logout(jti)
 └─ GET  /auth/me       → AuthService.currentUser(uid)

AuthServiceImpl
 ├─ LoginRateLimiter.tryAcquire(clientIp, username)
 ├─ UserQueryService.findByUsername(username)        → 账号 + 密文 + 状态
 ├─ PasswordEncoder.matches(raw, encoded)            （BCrypt）
 ├─ UserCommandService.createAccount(username, hash, nickname) → uid
 ├─ UserCommandService.updateLastLoginTime(uid)
 ├─ JwtSupport.issue(uid, username)                  → token / jti / 过期时间
 ├─ SessionStore.save(jti, uid, Duration.ofDays(7))
 ├─ SessionStore.remove(jti)
 └─ ApplicationEventPublisher.publishEvent(LoginEvent)

AuthInterceptor（roam-web/global）
 ├─ JwtSupport.parse(token)                          → uid / jti
 ├─ SessionStore.check(jti)                          → VALID / ABSENT / UNAVAILABLE
 ├─ UserQueryService.getById(uid)                    → 状态校验
 └─ CurrentUserContext.set(ReqInfo(uid, role))

CurrentUserContext（roam-core/context）
 └─ ThreadLocal 持有者：set / get / clear，由拦截器 afterCompletion 清理
```

依赖方向：`web → service → core → api`。`auth` 不直接 import `user` 的 repository，只调 `UserQueryService` / `UserCommandService`。

## 2. 接口

```java
// api/model/dto
public record RegisterCommand(String username, String password, String nickname) {}
public record LoginCommand(String username, String password) {}

// api/model/vo
public record AuthResult(String token, UserVO user) {}
public record ReqInfo(Long uid, String role) {}

// service/auth
public interface AuthService {
    AuthResult register(RegisterCommand cmd, String clientIp);
    AuthResult login(LoginCommand cmd, String clientIp);
    void logout(String jti);
    UserVO currentUser(long uid);
}

// service/user
public interface UserQueryService {
    Optional<UserAccount> findByUsername(String username);
    Optional<UserVO> getById(long uid);
}
public interface UserCommandService {
    long createAccount(String username, String encodedPassword, String nickname);
    void updateLastLoginTime(long uid);
}

// core/session
public interface SessionStore {
    void save(String jti, long uid, Duration ttl);
    SessionStatus check(String jti);      // VALID / ABSENT / UNAVAILABLE
    void remove(String jti);
    void revokeAllOf(long uid);
}

// core/limit
public interface LoginRateLimiter {
    boolean tryAcquire(String clientIp, String username);
}

// core/jwt
public final class JwtSupport {
    IssuedToken issue(long uid, String username);   // token / jti / expiresAt
    ParsedToken parse(String token) throws InvalidTokenException;
}
```

约定：

- 密码只以 BCrypt 密文出现在 `UserAccount` 里，`UserVO` 不含任何密码字段；
- `username` 在入库前统一 `trim()` + `toLowerCase(Locale.ROOT)`；
- `jti` 是 UUID，签发与登出都以它为准。

## 3. 失败语义

| 场景 | 返回 | 说明 |
|---|---|---|
| 注册：用户名已存在 | `409` | 依赖 DB 唯一索引，不做先查后插 |
| 注册：参数不合法 | `4001` | 用户名长度/字符集、密码长度 |
| 注册：Redis 写会话失败 | `503`「账号已创建，请直接登录」 | 号已建成，不能让用户以为注册失败 |
| 登录：用户不存在 | `401` 同一文案，**仍跑一次假 BCrypt** | 防枚举 + 防时序侧信道 |
| 登录：密码错 | `401` 同一文案 | 与上一条不可区分 |
| 登录：`status = 0` | `401` | 与密码错同文案 |
| 登录/注册：触发限流 | `429` | `ip + username` 维度的滑动窗口 |
| 拦截器：无 token | `401` | |
| 拦截器：签名错 / 过期 | `401`，**不查 Redis** | 伪造 token 不打存储 |
| 拦截器：Redis 无此 jti | `401` | 已登出或被踢 |
| 拦截器：Redis 不可用 | `503` | 依赖挂了不等于你没登录 |
| 拦截器：用户被禁用 / 已删除 | `401` | 每请求查一次状态，封禁立即生效 |
| 登出：token 无效 | `401` | |

## 4. 数据与副作用

| 位置 | 内容 |
|---|---|
| MySQL `user` | 经 `UserCommandService` 写入；密码为 BCrypt 密文 |
| Redis `roam:session:{jti}` | 值 = uid，TTL 7 天 |
| Redis `roam:auth:user-sessions:{uid}` | SET，成员为 jti，TTL 7 天；封禁/改密时整组 revoke |
| 事件 | `LoginEvent{uid, result, reason, ip}`，`AFTER_COMMIT` 消费 |

登录失败事件 `reason`：`NO_USER` / `BAD_PASSWORD` / `DISABLED` / `RATE_LIMITED`。

## 5. 测试接缝与首批用例

| 接缝 | 装配 | 首批用例 |
|---|---|---|
| HTTP | `@WebMvcTest` + 真 JWT + 假 `SessionStore` | `注册_成功_返回token`、`注册_用户名重复_返回409`、`登录_密码错_返回401`、`登录_Redis不可用_返回503` |
| Service | `@SpringBootTest` + 真 MySQL + 假 `SessionStore` | `注册_密码以密文入库`、`登录_用户不存在_仍调用一次编码器` |
| 建表 | Liquibase 对 `roam_bupt_test` | P1 补 |

P0 先做前两条接缝的四条用例，按切片逐条推进。

## 6. 明确不做

刷新令牌、改密码、多端设备管理、验证码、第三方登录。这些等出现真实需求再进本文件。

## 协调（谁给我数据 / 我给谁数据）

- **拿**：`user` 表的账号、密码哈希、role、status；只读，不写业务表。
- **给**：全站 `ReqInfo(uid, role, jti)`；每个受保护接口都依赖它。会话状态走 Redis（`roam:session:{jti}`）。
- **时序**：登录成功先写会话再返回 token；登出先删会话再返回。
- **跨域失败**：Redis 不可用 → 503（拒绝），不降级成放行；用户被禁用 → 401，每请求都查。
- **给 admin**：`revokeAllOf(uid)` 供封禁时撤销全部会话。
