package com.schwab.urlshortener.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caches short-code -> ShortUrl lookups for the redirect hot path.
 *
 * <p>Caught during review before this shipped: an unbounded/no-TTL cache here would let a link keep
 * redirecting past its expiresAt (or past a DELETE/deactivate) for as long as it stayed cached,
 * since {@code ShortUrlService#resolveForRedirect} only re-evaluates expiry on a cache miss. A
 * short TTL bounds that staleness window instead of eliminating the caching benefit; deactivation
 * also explicitly evicts (see ShortUrlService#deactivate). Trade-off: a link can still be reachable
 * for up to CACHE_TTL after it expires — acceptable for a prototype, called out in
 * docs/SCENARIOS.md.
 */
@Configuration
public class CacheConfig {

  public static final String SHORT_URL_LOOKUP_CACHE = "shortUrlLookup";
  private static final Duration CACHE_TTL = Duration.ofSeconds(30);

  @Bean
  public CacheManagerCustomizer<CaffeineCacheManager> caffeineCacheManagerCustomizer() {
    return cacheManager -> {
      cacheManager.setCacheNames(List.of(SHORT_URL_LOOKUP_CACHE));
      cacheManager.setCaffeine(
          Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(CACHE_TTL));
    };
  }
}
