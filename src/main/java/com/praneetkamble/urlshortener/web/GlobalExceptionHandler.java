package com.praneetkamble.urlshortener.web;

import com.praneetkamble.urlshortener.service.exception.RateLimitExceededException;
import com.praneetkamble.urlshortener.service.exception.ShortenerException;
import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Renders every error as an RFC 7807 {@code application/problem+json} document. */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ShortenerException.class)
    ResponseEntity<ProblemDetail> handleDomain(ShortenerException ex) {
        ProblemDetail problem = problem(ex.getStatus(), ex.getMessage());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.getStatus());
        if (ex instanceof RateLimitExceededException limited) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(limited.getResult().retryAfterSeconds()));
        }
        return response.body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, detail));
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("about:blank"));
        problem.setProperty("timestamp", Instant.now().toString());
        return problem;
    }
}
