package com.praneetkamble.urlshortener.service.exception;

import org.springframework.http.HttpStatus;

public class AliasUnavailableException extends ShortenerException {

    public AliasUnavailableException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
