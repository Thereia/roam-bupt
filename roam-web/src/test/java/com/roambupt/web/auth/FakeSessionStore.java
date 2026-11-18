package com.roambupt.web.auth;

import com.roambupt.core.session.SessionStatus;
import com.roambupt.core.session.SessionStore;
import com.roambupt.core.session.StoreUnavailableException;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试替身：内存会话存储，可切换为不可用状态。
 */
public class FakeSessionStore implements SessionStore {

    private final Map<String, Long> sessions = new ConcurrentHashMap<>();
    private volatile boolean unavailable = false;

    public void setUnavailable(boolean unavailable) {
        this.unavailable = unavailable;
    }

    public boolean contains(String jti) {
        return sessions.containsKey(jti);
    }

    @Override
    public void save(String jti, long uid, Duration ttl) {
        if (unavailable) {
            throw new StoreUnavailableException("fake unavailable", null);
        }
        sessions.put(jti, uid);
    }

    @Override
    public SessionStatus check(String jti) {
        if (unavailable) {
            return SessionStatus.UNAVAILABLE;
        }
        return sessions.containsKey(jti) ? SessionStatus.VALID : SessionStatus.ABSENT;
    }

    @Override
    public void remove(String jti) {
        sessions.remove(jti);
    }

    @Override
    public void revokeAllOf(long uid) {
        sessions.values().removeIf(value -> value == uid);
    }
}
