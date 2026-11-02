package com.praneetkamble.urlshortener.ratelimit;

import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Fixed-window rate limiter backed by Redis {@code INCR} + {@code EXPIRE}, so counters are shared by
 * every application instance. If Redis is unavailable the limiter <em>fails open</em>: availability of
 * the shortener is considered more important than strict throttling.
 */
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);

    private final StringRedisTemplate redis;
    private final int maxRequests;
    private final Duration window;
    private final Clock clock;

    public RedisRateLimiter(StringRedisTemplate redis, int maxRequests, Duration window, Clock clock) {
        this.redis = redis;
        this.maxRequests = maxRequests;
        this.window = window;
        this.clock = clock;
    }

    @Override
    public RateLimitResult tryAcquire(String key) {
        long windowMillis = window.toMillis();
        long now = clock.millis();
        long index = now / windowMillis;
        String redisKey = "shortener:rl:" + key + ":" + index;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            if (count == null) {
                return failOpen();
            }
            if (count == 1L) {
                redis.expire(redisKey, window.plusSeconds(1));
            }
            long remaining = Math.max(0, maxRequests - count);
            return new RateLimitResult(count <= maxRequests, maxRequests, remaining,
                    InMemoryRateLimiter.retryAfterSeconds(now, index, windowMillis));
        } catch (RuntimeException e) {
            log.warn("Redis rate limiter unavailable, failing open: {}", e.getMessage());
            return failOpen();
        }
    }

    private RateLimitResult failOpen() {
        return new RateLimitResult(true, maxRequests, maxRequests, 0);
    }
}
