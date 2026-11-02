package com.praneetkamble.urlshortener.ratelimit;

public record RateLimitResult(boolean allowed, int limit, long remaining, long retryAfterSeconds) {
}
