package com.roambupt.core.session;

import java.time.Duration;

/**
 * 会话白名单。TTL 写进签名，避免出现无过期时间的 key。
 */
public interface SessionStore {

    void save(String jti, long uid, Duration ttl);

    SessionStatus check(String jti);

    void remove(String jti);

    void revokeAllOf(long uid);
}
