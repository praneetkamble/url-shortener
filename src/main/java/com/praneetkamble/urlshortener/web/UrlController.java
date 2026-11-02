package com.praneetkamble.urlshortener.web;

import com.praneetkamble.urlshortener.domain.ShortUrl;
import com.praneetkamble.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/urls")
@Tag(name = "Short URLs", description = "Create, inspect and delete short links")
public class UrlController {

    private final UrlService service;

    public UrlController(UrlService service) {
        this.service = service;
    }

    @Operation(summary = "Create a short URL",
            description = "Rate limited per client address. Returns 409 if the custom alias is taken.")
    @PostMapping
    public ResponseEntity<UrlResponse> create(@Valid @RequestBody CreateUrlRequest request) {
        Duration ttl = request.expiresInSeconds() == null ? null : Duration.ofSeconds(request.expiresInSeconds());
        ShortUrl created = service.create(request.url(), request.customAlias(), ttl);
        UrlResponse body = UrlResponse.from(created, service.shortLink(created));
        return ResponseEntity.created(URI.create(body.shortUrl())).body(body);
    }

    @Operation(summary = "Get details of a short URL")
    @GetMapping("/{code}")
    public UrlResponse get(@PathVariable String code) {
        ShortUrl url = service.get(code);
        return UrlResponse.from(url, service.shortLink(url));
    }

    @Operation(summary = "Click analytics",
            description = "Total clicks, clicks per day for the last 30 days and the top referrers.")
    @GetMapping("/{code}/stats")
    public StatsResponse stats(@PathVariable String code) {
        UrlService.Stats stats = service.stats(code);
        return new StatsResponse(code, stats.url().getClickCount(), stats.clicksByDay(), stats.topReferrers());
    }

    @Operation(summary = "Delete a short URL and its analytics")
    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        service.delete(code);
        return ResponseEntity.noContent().build();
    }
}
