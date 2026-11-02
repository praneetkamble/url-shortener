package com.praneetkamble.urlshortener.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "shortener")
public record ShortenerProperties(String baseUrl, Duration cacheTtl, RateLimit rateLimit) {

    public ShortenerProperties {
        baseUrl = baseUrl == null ? "http://localhost:8080" : baseUrl.replaceAll("/+$", "");
        cacheTtl = cacheTtl == null ? Duration.ofHours(1) : cacheTtl;
        rateLimit = rateLimit == null ? new RateLimit("memory", 30, Duration.ofMinutes(1)) : rateLimit;
    }

    public record RateLimit(String store, int maxRequests, Duration window) {

        public RateLimit {
            store = store == null ? "memory" : store;
            maxRequests = maxRequests <= 0 ? 30 : maxRequests;
            window = window == null ? Duration.ofMinutes(1) : window;
        }
    }
}
