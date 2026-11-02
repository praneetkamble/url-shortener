package com.praneetkamble.urlshortener.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to create a short link")
public record CreateUrlRequest(
        @Schema(description = "Absolute http(s) URL to shorten", example = "https://example.com/some/very/long/path?x=1")
        @NotBlank @Size(max = 2048) String url,

        @Schema(description = "Optional custom alias (3-32 chars: letters, digits, '-' or '_')", example = "my-link")
        @Size(max = 64) String customAlias,

        @Schema(description = "Optional time-to-live in seconds (max 10 years)", example = "86400")
        @Min(1) @Max(315_360_000L) Long expiresInSeconds) {
}
