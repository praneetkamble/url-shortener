package com.praneetkamble.urlshortener.service;

import java.io.Serializable;
import java.time.Instant;

/** The minimal, cache-friendly projection needed to serve a redirect. */
public record ResolvedUrl(long id, String originalUrl, Instant expiresAt) implements Serializable {

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
