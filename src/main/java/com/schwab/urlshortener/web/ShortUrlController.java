package com.schwab.urlshortener.web;

import com.schwab.urlshortener.config.AppProperties;
import com.schwab.urlshortener.dto.CreateShortUrlRequest;
import com.schwab.urlshortener.dto.ShortUrlResponse;
import com.schwab.urlshortener.service.ShortUrlService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/urls")
public class ShortUrlController {

  private final ShortUrlService shortUrlService;
  private final AppProperties appProperties;

  public ShortUrlController(ShortUrlService shortUrlService, AppProperties appProperties) {
    this.shortUrlService = shortUrlService;
    this.appProperties = appProperties;
  }

  @PostMapping
  public ResponseEntity<ShortUrlResponse> create(
      @Valid @RequestBody CreateShortUrlRequest request) {
    var shortUrl = shortUrlService.create(request);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ShortUrlResponse.from(shortUrl, appProperties.baseUrl()));
  }

  @GetMapping("/{shortCode}")
  public ShortUrlResponse getMetadata(@PathVariable String shortCode) {
    var shortUrl = shortUrlService.getMetadata(shortCode);
    return ShortUrlResponse.from(shortUrl, appProperties.baseUrl());
  }

  @DeleteMapping("/{shortCode}")
  public ResponseEntity<Void> deactivate(@PathVariable String shortCode) {
    shortUrlService.deactivate(shortCode);
    return ResponseEntity.noContent().build();
  }
}
