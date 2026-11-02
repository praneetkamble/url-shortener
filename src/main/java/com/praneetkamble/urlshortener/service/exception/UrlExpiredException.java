package com.praneetkamble.urlshortener.service.exception;

import org.springframework.http.HttpStatus;

public class UrlExpiredException extends ShortenerException {

    public UrlExpiredException(String message) {
        super(HttpStatus.GONE, message);
    }
}
