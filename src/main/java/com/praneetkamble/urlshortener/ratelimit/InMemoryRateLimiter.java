package com.praneetkamble.urlshortener.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fixed-window rate limiter held in process memory. Good for a single instance and for tests;
 * use {@link RedisRateLimiter} when several instances must share counters.
 */
public class InMemoryRateLimiter implements RateLimiter {

    private static final int CLEANUP_THRESHOLD = 10_000;

    private record Window(long index, int count) {
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int maxRequests;
    private final long windowMillis;
    private final Clock clock;

    public InMemoryRateLimiter(int maxRequests, Duration window, Clock clock) {
        if (maxRequests <= 0 || window.isZero() || window.isNegative()) {
            throw new IllegalArgumentException("maxRequests and window must be positive");
        }
        this.maxRequests = maxRequests;
        this.windowMillis = window.toMillis();
        this.clock = clock;
    }

    @Override
    public RateLimitResult tryAcquire(String key) {
        long now = clock.millis();
        long index = now / windowMillis;
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> w.index() < index);
        }
        Window updated = windows.merge(key, new Window(index, 1),
                (old, fresh) -> old.index() == index ? new Window(index, old.count() + 1) : fresh);
        boolean allowed = updated.count() <= maxRequests;
        long remaining = Math.max(0, maxRequests - updated.count());
        return new RateLimitResult(allowed, maxRequests, remaining, retryAfterSeconds(now, index, windowMillis));
    }

    static long retryAfterSeconds(long nowMillis, long windowIndex, long windowMillis) {
        long windowEnd = (windowIndex + 1) * windowMillis;
        return Math.max(1, (windowEnd - nowMillis + 999) / 1000);
    }
}
