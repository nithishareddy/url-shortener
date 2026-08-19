package com.schwab.urlshortener.dto;

import com.schwab.urlshortener.domain.ShortUrl;
import java.time.Instant;

public record ShortUrlResponse(String shortCode, String shortUrl, String longUrl, Instant createdAt) {

  public static ShortUrlResponse from(ShortUrl entity, String baseUrl) {
    return new ShortUrlResponse(
        entity.getShortCode(), baseUrl + "/" + entity.getShortCode(), entity.getLongUrl(), entity.getCreatedAt());
  }
}
