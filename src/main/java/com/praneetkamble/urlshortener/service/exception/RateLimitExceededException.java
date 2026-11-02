package com.praneetkamble.urlshortener.service.exception;

import com.praneetkamble.urlshortener.ratelimit.RateLimitResult;
import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends ShortenerException {

    private final transient RateLimitResult result;

    public RateLimitExceededException(RateLimitResult result) {
        super(HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded. Retry in " + result.retryAfterSeconds() + "s.");
        this.result = result;
    }

    public RateLimitResult getResult() {
        return result;
    }
}
