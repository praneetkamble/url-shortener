package com.praneetkamble.urlshortener.ratelimit;

/** Decides whether a caller identified by {@code key} may perform another request right now. */
public interface RateLimiter {

    RateLimitResult tryAcquire(String key);
}
