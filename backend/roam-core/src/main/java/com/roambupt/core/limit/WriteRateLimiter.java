package com.roambupt.core.limit;

/** 写操作限流：发帖、评论、上传、打分等。 */
public interface WriteRateLimiter {

    boolean tryAcquire(String key);
}
