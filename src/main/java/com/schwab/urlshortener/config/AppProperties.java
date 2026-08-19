package com.schwab.urlshortener.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String baseUrl, RateLimit rateLimit, List<String> reservedAliases) {

  public record RateLimit(Create create) {
    public record Create(int capacity, int refillTokens, int refillDurationSeconds) {}
  }
}
