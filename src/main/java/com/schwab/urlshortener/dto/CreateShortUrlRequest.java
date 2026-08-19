package com.schwab.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateShortUrlRequest(
    @NotBlank(message = "longUrl is required") @Size(max = 2048) String longUrl,
    @Pattern(
            regexp = "^[A-Za-z0-9_-]{3,20}$",
            message = "customAlias must be 3-20 chars of letters, digits, '_' or '-'")
        String customAlias,
    Instant expiresAt) {}
