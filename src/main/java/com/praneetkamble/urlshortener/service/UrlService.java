package com.praneetkamble.urlshortener.service;

import com.praneetkamble.urlshortener.config.ShortenerProperties;
import com.praneetkamble.urlshortener.domain.ClickEvent;
import com.praneetkamble.urlshortener.domain.DailyClicks;
import com.praneetkamble.urlshortener.domain.ReferrerCount;
import com.praneetkamble.urlshortener.domain.ShortUrl;
import com.praneetkamble.urlshortener.repository.ClickEventRepository;
import com.praneetkamble.urlshortener.repository.ShortUrlRepository;
import com.praneetkamble.urlshortener.service.exception.AliasUnavailableException;
import com.praneetkamble.urlshortener.service.exception.InvalidUrlException;
import com.praneetkamble.urlshortener.service.exception.UrlExpiredException;
import com.praneetkamble.urlshortener.service.exception.UrlNotFoundException;
import com.praneetkamble.urlshortener.util.Base62;
import com.praneetkamble.urlshortener.util.UrlValidator;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);

    public static final Pattern ALIAS_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{3,32}$");
    static final Set<String> RESERVED = Set.of("api", "actuator", "swagger-ui", "v3", "docs", "health", "error");
    static final Duration MAX_TTL = Duration.ofDays(3650);
    static final int STATS_DAYS = 30;
    static final int TOP_REFERRERS = 5;
    private static final int MAX_ID_ATTEMPTS = 5;

    private final ShortUrlRepository urls;
    private final ClickEventRepository clicks;
    private final UrlResolver resolver;
    private final ShortenerProperties properties;
    private final Clock clock;

    public UrlService(ShortUrlRepository urls, ClickEventRepository clicks, UrlResolver resolver,
                      ShortenerProperties properties, Clock clock) {
        this.urls = urls;
        this.clicks = clicks;
        this.resolver = resolver;
        this.properties = properties;
        this.clock = clock;
    }

    /** Creates a short link. {@code alias} and {@code ttl} are optional. */
    @Transactional
    public ShortUrl create(String originalUrl, String alias, Duration ttl) {
        String target = originalUrl == null ? null : originalUrl.trim();
        if (!UrlValidator.isValid(target)) {
            throw new InvalidUrlException("URL must be an absolute http(s) URL of at most "
                    + UrlValidator.MAX_LENGTH + " characters");
        }
        if (isSelfReferential(target)) {
            throw new InvalidUrlException("Shortening links that already point to this service is not allowed");
        }
        Instant now = clock.instant();
        Instant expiresAt = null;
        if (ttl != null) {
            if (ttl.isZero() || ttl.isNegative() || ttl.compareTo(MAX_TTL) > 0) {
                throw new InvalidUrlException("Expiry must be between 1 second and " + MAX_TTL.toDays() + " days");
            }
            expiresAt = now.plus(ttl);
        }
        return alias == null || alias.isBlank()
                ? createGenerated(target, now, expiresAt)
                : createWithAlias(target, alias, now, expiresAt);
    }

    private ShortUrl createWithAlias(String target, String alias, Instant now, Instant expiresAt) {
        if (!ALIAS_PATTERN.matcher(alias).matches()) {
            throw new InvalidUrlException("Alias must be 3-32 characters of letters, digits, '-' or '_'");
        }
        if (RESERVED.contains(alias.toLowerCase(Locale.ROOT)) || urls.existsByCode(alias)) {
            throw new AliasUnavailableException("Alias '" + alias + "' is not available");
        }
        try {
            return urls.saveAndFlush(new ShortUrl(urls.nextId(), alias, target, true, now, expiresAt));
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent request for the same alias.
            throw new AliasUnavailableException("Alias '" + alias + "' is not available");
        }
    }

    private ShortUrl createGenerated(String target, Instant now, Instant expiresAt) {
        for (int attempt = 0; attempt < MAX_ID_ATTEMPTS; attempt++) {
            long id = urls.nextId();
            String code = Base62.encode(id);
            // A custom alias may already occupy this code; just draw the next id.
            if (!urls.existsByCode(code)) {
                return urls.saveAndFlush(new ShortUrl(id, code, target, false, now, expiresAt));
            }
        }
        throw new IllegalStateException("Could not allocate a unique short code");
    }

    @Transactional(readOnly = true)
    public ShortUrl get(String code) {
        return urls.findByCode(code)
                .orElseThrow(() -> new UrlNotFoundException("No short URL exists for code '" + code + "'"));
    }

    /** Resolves a code for redirecting and records the click. */
    @Transactional
    public String redirect(String code, String referrer, String userAgent) {
        ResolvedUrl resolved = resolver.resolve(code);
        Instant now = clock.instant();
        if (resolved.isExpired(now)) {
            throw new UrlExpiredException("Short URL '" + code + "' has expired");
        }
        clicks.save(new ClickEvent(resolved.id(), now, referrerHost(referrer), truncate(userAgent, 512)));
        urls.incrementClickCount(resolved.id());
        return resolved.originalUrl();
    }

    @Transactional(readOnly = true)
    public Stats stats(String code) {
        ShortUrl url = get(code);
        Instant since = clock.instant().minus(Duration.ofDays(STATS_DAYS));
        List<DailyClicks> daily = clicks.countByDay(url.getId(), since);
        List<ReferrerCount> referrers = clicks.topReferrers(url.getId(), PageRequest.of(0, TOP_REFERRERS));
        return new Stats(url, daily, referrers);
    }

    @Transactional
    public void delete(String code) {
        ShortUrl url = get(code);
        urls.delete(url); // click events are removed by ON DELETE CASCADE
        resolver.evict(code);
    }

    /** Hourly housekeeping: expired links are already unreachable, this just reclaims the rows. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT1M")
    @Transactional
    public void purgeExpired() {
        int removed = urls.deleteExpiredBefore(clock.instant().minus(Duration.ofDays(1)));
        if (removed > 0) {
            log.info("Purged {} expired short URLs", removed);
        }
    }

    public String shortLink(ShortUrl url) {
        return properties.baseUrl() + "/" + url.getCode();
    }

    private boolean isSelfReferential(String target) {
        try {
            URI self = URI.create(properties.baseUrl());
            URI candidate = URI.create(target);
            return self.getHost() != null && self.getHost().equalsIgnoreCase(candidate.getHost())
                    && self.getPort() == candidate.getPort();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static String referrerHost(String referrer) {
        if (referrer == null || referrer.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(referrer.trim()).getHost();
            return host == null ? null : truncate(host.toLowerCase(Locale.ROOT), 255);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public record Stats(ShortUrl url, List<DailyClicks> clicksByDay, List<ReferrerCount> topReferrers) {
    }
}
