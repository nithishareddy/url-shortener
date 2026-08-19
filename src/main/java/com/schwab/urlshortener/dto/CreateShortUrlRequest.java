package com.schwab.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateShortUrlRequest(
    @NotBlank(message = "longUrl is required") @Size(max = 2048) String longUrl) {}
