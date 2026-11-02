package com.praneetkamble.urlshortener.service.exception;

import org.springframework.http.HttpStatus;

/** Base class for domain errors that map directly to an HTTP status. */
public abstract class ShortenerException extends RuntimeException {

    private final HttpStatus status;

    protected ShortenerException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
