package com.praneetkamble.urlshortener.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

    private MutableClock clock;
    private InMemoryRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:10Z"));
        limiter = new InMemoryRateLimiter(3, Duration.ofMinutes(1), clock);
    }

    @Test
    void allowsUpToTheLimitThenRejects() {
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        assertThat(limiter.tryAcquire("a").allowed()).isTrue();
        RateLimitResult third = limiter.tryAcquire("a");
        assertThat(third.allowed()).isTrue();
        assertThat(third.remaining()).isZero();

        RateLimitResult fourth = limiter.tryAcquire("a");
        assertThat(fourth.allowed()).isFalse();
        assertThat(fourth.retryAfterSeconds()).isEqualTo(50);
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("a");
        }
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();
        assertThat(limiter.tryAcquire("b").allowed()).isTrue();
    }

    @Test
    void budgetResetsWhenTheWindowRollsOver() {
        for (int i = 0; i < 4; i++) {
            limiter.tryAcquire("a");
        }
        assertThat(limiter.tryAcquire("a").allowed()).isFalse();

        clock.advance(Duration.ofSeconds(50));
        RateLimitResult fresh = limiter.tryAcquire("a");
        assertThat(fresh.allowed()).isTrue();
        assertThat(fresh.remaining()).isEqualTo(2);
    }

    @Test
    void isThreadSafe() throws Exception {
        InMemoryRateLimiter shared = new InMemoryRateLimiter(100, Duration.ofMinutes(1), clock);
        AtomicInteger allowed = new AtomicInteger();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            IntStream.range(0, 1000).forEach(i -> pool.submit(() -> {
                if (shared.tryAcquire("same").allowed()) {
                    allowed.incrementAndGet();
                }
            }));
            pool.shutdown();
            assertThat(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        }
        assertThat(allowed.get()).isEqualTo(100);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new InMemoryRateLimiter(0, Duration.ofMinutes(1), clock))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InMemoryRateLimiter(1, Duration.ZERO, clock))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
