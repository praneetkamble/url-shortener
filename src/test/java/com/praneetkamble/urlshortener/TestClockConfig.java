package com.praneetkamble.urlshortener;

import com.praneetkamble.urlshortener.ratelimit.MutableClock;
import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestClockConfig {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(Instant.parse("2026-01-15T10:00:00Z"));
    }
}
