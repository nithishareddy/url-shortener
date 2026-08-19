package com.schwab.urlshortener.web;

import com.schwab.urlshortener.service.ShortUrlService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

  private final ShortUrlService shortUrlService;

  public RedirectController(ShortUrlService shortUrlService) {
    this.shortUrlService = shortUrlService;
  }

  @GetMapping("/{shortCode}")
  public ResponseEntity<Void> redirect(@PathVariable String shortCode) {
    var shortUrl = shortUrlService.resolveForRedirect(shortCode);
    return ResponseEntity.status(HttpStatus.FOUND)
        .header(HttpHeaders.LOCATION, shortUrl.getLongUrl())
        .build();
  }
}
