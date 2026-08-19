package com.schwab.urlshortener.ratelimit;

import com.schwab.urlshortener.config.AppProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client-IP token-bucket limit on link creation (the write path most worth protecting from
 * abuse/spam). In-memory buckets: correct for a single instance; a multi-instance deployment would
 * need a shared store (e.g. Redis-backed Bucket4j) for the limit to hold cluster-wide — noted as a
 * scale-out limitation, not built here (YAGNI for a prototype).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final AppProperties.RateLimit.Create limitConfig;

  public RateLimitFilter(AppProperties appProperties) {
    this.limitConfig = appProperties.rateLimit().create();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !("POST".equalsIgnoreCase(request.getMethod())
        && "/api/urls".equals(request.getRequestURI()));
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Bucket bucket = buckets.computeIfAbsent(clientKey(request), key -> newBucket());
    if (bucket.tryConsume(1)) {
      chain.doFilter(request, response);
    } else {
      response.setStatus(429);
      response.setContentType("application/problem+json");
      response
          .getWriter()
          .write("{\"status\":429,\"detail\":\"Rate limit exceeded, try again later\"}");
    }
  }

  private Bucket newBucket() {
    Bandwidth limit =
        Bandwidth.builder()
            .capacity(limitConfig.capacity())
            .refillGreedy(
                limitConfig.refillTokens(), Duration.ofSeconds(limitConfig.refillDurationSeconds()))
            .build();
    return Bucket.builder().addLimit(limit).build();
  }

  private String clientKey(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
