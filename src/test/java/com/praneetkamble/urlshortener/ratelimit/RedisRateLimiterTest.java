package com.praneetkamble.urlshortener.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisRateLimiterTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private RedisRateLimiter limiter;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:10Z"));
        limiter = new RedisRateLimiter(redis, 3, Duration.ofMinutes(1), clock);
    }

    @Test
    void firstRequestSetsExpiryOnTheWindowKey() {
        when(ops.increment(anyString())).thenReturn(1L);

        RateLimitResult result = limiter.tryAcquire("1.2.3.4");

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(2);
        verify(redis).expire("shortener:rl:1.2.3.4:" + (Instant.parse("2026-01-01T00:00:10Z").toEpochMilli() / 60_000),
                Duration.ofSeconds(61));
    }

    @Test
    void rejectsOnceCounterExceedsLimitWithoutResettingExpiry() {
        when(ops.increment(anyString())).thenReturn(4L);

        RateLimitResult result = limiter.tryAcquire("1.2.3.4");

        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining()).isZero();
        assertThat(result.retryAfterSeconds()).isEqualTo(50);
        verify(redis, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void failsOpenWhenRedisIsDown() {
        when(ops.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(limiter.tryAcquire("1.2.3.4").allowed()).isTrue();
    }
}
