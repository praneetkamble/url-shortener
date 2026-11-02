package com.praneetkamble.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.praneetkamble.urlshortener.AbstractIntegrationTest;
import com.praneetkamble.urlshortener.domain.ShortUrl;
import com.praneetkamble.urlshortener.repository.ShortUrlRepository;
import com.praneetkamble.urlshortener.service.exception.AliasUnavailableException;
import com.praneetkamble.urlshortener.service.exception.InvalidUrlException;
import com.praneetkamble.urlshortener.service.exception.UrlExpiredException;
import com.praneetkamble.urlshortener.service.exception.UrlNotFoundException;
import com.praneetkamble.urlshortener.util.Base62;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UrlServiceTest extends AbstractIntegrationTest {

    @Autowired
    UrlService service;
    @Autowired
    ShortUrlRepository repository;

    @Test
    void generatesUniqueBase62CodesOfSensibleLength() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            ShortUrl url = service.create("https://example.com/page/" + i, null, null);
            assertThat(url.getCode()).matches("[0-9A-Za-z]{3,8}");
            assertThat(url.isCustomAlias()).isFalse();
            assertThat(codes.add(url.getCode())).isTrue();
        }
    }

    @Test
    void trimsTheTargetAndStoresCreationTime() {
        ShortUrl url = service.create("  https://example.com/x  ", null, null);
        assertThat(url.getOriginalUrl()).isEqualTo("https://example.com/x");
        assertThat(url.getCreatedAt()).isEqualTo(START);
        assertThat(url.getExpiresAt()).isNull();
    }

    @Test
    void acceptsCustomAliasAndRejectsDuplicates() {
        ShortUrl url = service.create("https://example.com/a", "svc-alias_1", null);
        assertThat(url.getCode()).isEqualTo("svc-alias_1");
        assertThat(url.isCustomAlias()).isTrue();

        assertThatThrownBy(() -> service.create("https://example.com/b", "svc-alias_1", null))
                .isInstanceOf(AliasUnavailableException.class);
    }

    @Test
    void rejectsMalformedAndReservedAliases() {
        assertThatThrownBy(() -> service.create("https://example.com", "ab", null))
                .isInstanceOf(InvalidUrlException.class);
        assertThatThrownBy(() -> service.create("https://example.com", "has space", null))
                .isInstanceOf(InvalidUrlException.class);
        assertThatThrownBy(() -> service.create("https://example.com", "API", null))
                .isInstanceOf(AliasUnavailableException.class);
    }

    @Test
    void generatedCodeSkipsCodesTakenByCustomAliases() {
        // Reserve the code the generator is about to hand out.
        String upcoming = Base62.encode(repository.nextId() + 1);
        service.create("https://example.com/alias", upcoming, null);

        ShortUrl generated = service.create("https://example.com/generated", null, null);

        assertThat(generated.getCode()).isNotEqualTo(upcoming);
        assertThat(service.get(upcoming).getOriginalUrl()).isEqualTo("https://example.com/alias");
    }

    @Test
    void rejectsInvalidUrlsAndTtls() {
        assertThatThrownBy(() -> service.create("not a url", null, null)).isInstanceOf(InvalidUrlException.class);
        assertThatThrownBy(() -> service.create("ftp://example.com", null, null)).isInstanceOf(InvalidUrlException.class);
        assertThatThrownBy(() -> service.create("https://example.com", null, Duration.ZERO))
                .isInstanceOf(InvalidUrlException.class);
        assertThatThrownBy(() -> service.create("https://example.com", null, Duration.ofDays(4000)))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void refusesToShortenItself() {
        assertThatThrownBy(() -> service.create("http://short.test/abc", null, null))
                .isInstanceOf(InvalidUrlException.class);
    }

    @Test
    void redirectCountsClicksAndAggregatesReferrers() {
        ShortUrl url = service.create("https://example.com/target", null, null);

        assertThat(service.redirect(url.getCode(), "https://news.example.org/story", "JUnit")).isEqualTo("https://example.com/target");
        service.redirect(url.getCode(), "https://news.example.org/other", "JUnit");
        service.redirect(url.getCode(), null, "JUnit");
        clock.advance(Duration.ofDays(1));
        service.redirect(url.getCode(), "https://t.co/x", null);

        UrlService.Stats stats = service.stats(url.getCode());
        assertThat(stats.url().getClickCount()).isEqualTo(4);
        assertThat(stats.clicksByDay()).extracting("date", "clicks")
                .containsExactly(org.assertj.core.groups.Tuple.tuple(java.time.LocalDate.parse("2026-01-15"), 3L),
                        org.assertj.core.groups.Tuple.tuple(java.time.LocalDate.parse("2026-01-16"), 1L));
        assertThat(stats.topReferrers()).extracting("referrer", "clicks")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("news.example.org", 2L),
                        org.assertj.core.groups.Tuple.tuple("direct", 1L),
                        org.assertj.core.groups.Tuple.tuple("t.co", 1L));
    }

    @Test
    void expiredLinksAreGoneAndDoNotCountClicks() {
        ShortUrl url = service.create("https://example.com/temp", null, Duration.ofMinutes(10));
        service.redirect(url.getCode(), null, null);

        clock.advance(Duration.ofMinutes(10));

        assertThatThrownBy(() -> service.redirect(url.getCode(), null, null)).isInstanceOf(UrlExpiredException.class);
        assertThat(service.get(url.getCode()).getClickCount()).isEqualTo(1);
    }

    @Test
    void deleteRemovesLinkAndEvictsItFromTheCache() {
        ShortUrl url = service.create("https://example.com/delete-me", null, null);
        service.redirect(url.getCode(), null, null); // warms the cache

        service.delete(url.getCode());

        assertThatThrownBy(() -> service.redirect(url.getCode(), null, null)).isInstanceOf(UrlNotFoundException.class);
        assertThatThrownBy(() -> service.get(url.getCode())).isInstanceOf(UrlNotFoundException.class);
    }

    @Test
    void purgeRemovesLinksExpiredForMoreThanADay() {
        ShortUrl url = service.create("https://example.com/old", null, Duration.ofMinutes(1));
        clock.advance(Duration.ofDays(2));

        service.purgeExpired();

        assertThat(repository.findByCode(url.getCode())).isEmpty();
    }

    @Test
    void extractsReferrerHosts() {
        assertThat(UrlService.referrerHost("https://Example.COM/path?q=1")).isEqualTo("example.com");
        assertThat(UrlService.referrerHost("")).isNull();
        assertThat(UrlService.referrerHost("not a uri")).isNull();
        assertThat(UrlService.referrerHost(null)).isNull();
    }
}
