package com.praneetkamble.urlshortener.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class UrlValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com", "http://example.com/a/b?x=1#frag", "HTTPS://Example.com:8443/p",
            "https://sub.domain.example.co.uk/path"})
    void acceptsHttpAndHttpsUrls(String url) {
        assertThat(UrlValidator.isValid(url)).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "example.com", "ftp://example.com", "javascript:alert(1)", "file:///etc/passwd",
            "http://", "http:///nohost", "https://user:pw@example.com", "http://exa mple.com", "mailto:a@b.c"})
    void rejectsEverythingElse(String url) {
        assertThat(UrlValidator.isValid(url)).isFalse();
    }

    @Test
    void rejectsOverlongUrls() {
        String url = "https://example.com/" + "a".repeat(UrlValidator.MAX_LENGTH);
        assertThat(UrlValidator.isValid(url)).isFalse();
    }
}
