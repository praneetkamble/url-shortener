package com.praneetkamble.urlshortener.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/** Validates user supplied target URLs. Only absolute http(s) URLs with a host are accepted. */
public final class UrlValidator {

    public static final int MAX_LENGTH = 2048;
    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private UrlValidator() {
    }

    public static boolean isValid(String url) {
        if (url == null || url.isBlank() || url.length() > MAX_LENGTH) {
            return false;
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            return scheme != null
                    && ALLOWED_SCHEMES.contains(scheme.toLowerCase())
                    && host != null
                    && !host.isBlank()
                    && uri.getUserInfo() == null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
