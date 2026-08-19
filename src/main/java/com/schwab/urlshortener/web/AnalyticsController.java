package com.schwab.urlshortener.web;

import com.schwab.urlshortener.dto.AnalyticsResponse;
import com.schwab.urlshortener.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/urls")
public class AnalyticsController {

  private final AnalyticsService analyticsService;

  public AnalyticsController(AnalyticsService analyticsService) {
    this.analyticsService = analyticsService;
  }

  @GetMapping("/{shortCode}/analytics")
  public AnalyticsResponse getAnalytics(@PathVariable String shortCode) {
    return analyticsService.getAnalytics(shortCode);
  }
}
