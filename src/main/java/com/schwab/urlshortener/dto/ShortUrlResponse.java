package com.schwab.urlshortener.dto;

import com.schwab.urlshortener.domain.ShortUrl;
import java.time.Instant;

public record ShortUrlResponse(
    String shortCode,
    String shortUrl,
    String longUrl,
    boolean customAlias,
    Instant createdAt,
    Instant expiresAt,
    boolean active) {

  public static ShortUrlResponse from(ShortUrl entity, String baseUrl) {
    return new ShortUrlResponse(
        entity.getShortCode(),
        baseUrl + "/" + entity.getShortCode(),
        entity.getLongUrl(),
        entity.isCustomAlias(),
        entity.getCreatedAt(),
        entity.getExpiresAt(),
        entity.isActive());
  }
}
