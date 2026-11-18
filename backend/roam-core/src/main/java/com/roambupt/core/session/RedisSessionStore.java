package com.roambupt.core.session;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Set;

/**
 * Redis 会话白名单实现。
 *
 * <p>两个 key：{@code roam:session:{jti}} 判存在；{@code roam:auth:user-sessions:{uid}}
 * 记录某个用户签发的全部 jti，用于封禁/改密时一次撤销。</p>
 */
@Repository
public class RedisSessionStore implements SessionStore {

    private static final String SESSION_KEY = "roam:session:";
    private static final String USER_SESSIONS_KEY = "roam:auth:user-sessions:";

    private final StringRedisTemplate redis;

    public RedisSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void save(String jti, long uid, Duration ttl) {
        try {
            redis.opsForValue().set(SESSION_KEY + jti, Long.toString(uid), ttl);
            String userKey = USER_SESSIONS_KEY + uid;
            redis.opsForSet().add(userKey, jti);
            redis.expire(userKey, ttl);
        } catch (DataAccessException e) {
            throw new StoreUnavailableException("会话存储不可用", e);
        }
    }

    @Override
    public SessionStatus check(String jti) {
        try {
            String uid = redis.opsForValue().get(SESSION_KEY + jti);
            return uid == null ? SessionStatus.ABSENT : SessionStatus.VALID;
        } catch (DataAccessException e) {
            return SessionStatus.UNAVAILABLE;
        }
    }

    @Override
    public void remove(String jti) {
        try {
            String uid = redis.opsForValue().get(SESSION_KEY + jti);
            redis.delete(SESSION_KEY + jti);
            if (uid != null) {
                redis.opsForSet().remove(USER_SESSIONS_KEY + uid, jti);
            }
        } catch (DataAccessException e) {
            throw new StoreUnavailableException("会话存储不可用", e);
        }
    }

    @Override
    public void revokeAllOf(long uid) {
        try {
            String userKey = USER_SESSIONS_KEY + uid;
            Set<String> jtis = redis.opsForSet().members(userKey);
            if (jtis != null) {
                for (String jti : jtis) {
                    redis.delete(SESSION_KEY + jti);
                }
            }
            redis.delete(userKey);
        } catch (DataAccessException e) {
            throw new StoreUnavailableException("会话存储不可用", e);
        }
    }
}
