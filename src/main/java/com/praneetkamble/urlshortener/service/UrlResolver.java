package com.praneetkamble.urlshortener.service;

import com.praneetkamble.urlshortener.repository.ShortUrlRepository;
import com.praneetkamble.urlshortener.service.exception.UrlNotFoundException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-through cache in front of the database for the hot redirect path. Lives in its own bean so that
 * Spring's caching proxy is applied (self-invocation would bypass it). Misses throw instead of returning
 * null, so unknown codes are never cached.
 */
@Component
public class UrlResolver {

    public static final String CACHE_NAME = "urls";

    private final ShortUrlRepository repository;

    public UrlResolver(ShortUrlRepository repository) {
        this.repository = repository;
    }

    @Cacheable(cacheNames = CACHE_NAME, key = "#code")
    @Transactional(readOnly = true)
    public ResolvedUrl resolve(String code) {
        return repository.findByCode(code)
                .map(u -> new ResolvedUrl(u.getId(), u.getOriginalUrl(), u.getExpiresAt()))
                .orElseThrow(() -> new UrlNotFoundException("No short URL exists for code '" + code + "'"));
    }

    @CacheEvict(cacheNames = CACHE_NAME, key = "#code")
    public void evict(String code) {
        // eviction only
    }
}
