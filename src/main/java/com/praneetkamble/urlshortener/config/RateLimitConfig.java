package com.praneetkamble.urlshortener.config;

import com.praneetkamble.urlshortener.ratelimit.InMemoryRateLimiter;
import com.praneetkamble.urlshortener.ratelimit.RateLimitInterceptor;
import com.praneetkamble.urlshortener.ratelimit.RateLimiter;
import com.praneetkamble.urlshortener.ratelimit.RedisRateLimiter;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RateLimitConfig {

    @Bean
    @ConditionalOnProperty(name = "shortener.rate-limit.store", havingValue = "redis")
    RateLimiter redisRateLimiter(StringRedisTemplate redis, ShortenerProperties props, Clock clock) {
        ShortenerProperties.RateLimit rl = props.rateLimit();
        return new RedisRateLimiter(redis, rl.maxRequests(), rl.window(), clock);
    }

    @Bean
    @ConditionalOnProperty(name = "shortener.rate-limit.store", havingValue = "memory", matchIfMissing = true)
    RateLimiter inMemoryRateLimiter(ShortenerProperties props, Clock clock) {
        ShortenerProperties.RateLimit rl = props.rateLimit();
        return new InMemoryRateLimiter(rl.maxRequests(), rl.window(), clock);
    }

    @Bean
    RateLimitInterceptor rateLimitInterceptor(RateLimiter limiter) {
        return new RateLimitInterceptor(limiter);
    }
}
