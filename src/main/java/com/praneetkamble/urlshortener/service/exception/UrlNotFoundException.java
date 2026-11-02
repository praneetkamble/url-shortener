package com.praneetkamble.urlshortener.service.exception;

import org.springframework.http.HttpStatus;

public class UrlNotFoundException extends ShortenerException {

    public UrlNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
