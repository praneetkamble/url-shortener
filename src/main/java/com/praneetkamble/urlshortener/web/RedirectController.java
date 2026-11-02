package com.praneetkamble.urlshortener.web;

import com.praneetkamble.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Redirect")
public class RedirectController {

    private final UrlService service;

    public RedirectController(UrlService service) {
        this.service = service;
    }

    /**
     * Uses 302 (not 301) on purpose: browsers cache permanent redirects and would stop hitting the
     * service, which would break click analytics and expiry.
     */
    @Operation(summary = "Follow a short link", description = "302 to the target; 404 if unknown, 410 if expired.")
    @GetMapping("/{code:[A-Za-z0-9_-]{3,32}}")
    public ResponseEntity<Void> redirect(@PathVariable String code,
                                         @RequestHeader(value = HttpHeaders.REFERER, required = false) String referer,
                                         @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        String target = service.redirect(code, referer, userAgent);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(target))
                .cacheControl(CacheControl.noStore())
                .build();
    }
}
