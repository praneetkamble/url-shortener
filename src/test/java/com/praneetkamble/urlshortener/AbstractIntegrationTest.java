package com.praneetkamble.urlshortener;

import com.praneetkamble.urlshortener.ratelimit.MutableClock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Shared Spring context (H2 in PostgreSQL mode, simple cache, in-memory rate limiter, movable clock). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
public abstract class AbstractIntegrationTest {

    protected static final Instant START = Instant.parse("2026-01-15T10:00:00Z");
    private static final AtomicInteger CLIENT = new AtomicInteger();

    @Autowired
    protected MutableClock clock;

    @BeforeEach
    void resetClock() {
        clock.set(START);
    }

    /** A unique client address per call so tests never share a rate-limit budget. */
    protected static String newClientIp() {
        int n = CLIENT.incrementAndGet();
        return "10.1." + (n / 250) + "." + (n % 250 + 1);
    }
}
