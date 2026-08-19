package com.schwab.urlshortener.web;

import com.schwab.urlshortener.service.ClickTrackingService;
import com.schwab.urlshortener.service.ShortUrlService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

  private final ShortUrlService shortUrlService;
  private final ClickTrackingService clickTrackingService;

  public RedirectController(ShortUrlService shortUrlService, ClickTrackingService clickTrackingService) {
    this.shortUrlService = shortUrlService;
    this.clickTrackingService = clickTrackingService;
  }

  @GetMapping("/{shortCode}")
  public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
    var shortUrl = shortUrlService.resolveForRedirect(shortCode);
    clickTrackingService.recordClick(
        shortUrl.getId(), request.getHeader(HttpHeaders.REFERER), request.getHeader(HttpHeaders.USER_AGENT));
    return ResponseEntity.status(HttpStatus.FOUND)
        .header(HttpHeaders.LOCATION, shortUrl.getLongUrl())
        .build();
  }
}
