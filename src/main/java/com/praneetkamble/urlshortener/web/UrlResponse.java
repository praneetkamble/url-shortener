package com.praneetkamble.urlshortener.web;

import com.praneetkamble.urlshortener.domain.ShortUrl;
import java.time.Instant;

public record UrlResponse(String code, String shortUrl, String originalUrl, boolean customAlias,
                          Instant createdAt, Instant expiresAt, long clickCount) {

    public static UrlResponse from(ShortUrl url, String shortLink) {
        return new UrlResponse(url.getCode(), shortLink, url.getOriginalUrl(), url.isCustomAlias(),
                url.getCreatedAt(), url.getExpiresAt(), url.getClickCount());
    }
}
