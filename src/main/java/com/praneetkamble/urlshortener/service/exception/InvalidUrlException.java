package com.praneetkamble.urlshortener.service.exception;

import org.springframework.http.HttpStatus;

public class InvalidUrlException extends ShortenerException {

    public InvalidUrlException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
