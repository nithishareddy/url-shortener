package com.schwab.urlshortener.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schwab.urlshortener.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

  @Mock private HttpServletRequest request;
  @Mock private HttpServletResponse response;
  @Mock private FilterChain chain;

  private RateLimitFilter filter;

  @BeforeEach
  void setUp() {
    AppProperties appProperties =
        new AppProperties(
            "http://localhost:8080",
            new AppProperties.RateLimit(new AppProperties.RateLimit.Create(2, 2, 60)),
            null);
    filter = new RateLimitFilter(appProperties);
  }

  @Test
  void onlyFiltersPostToCreateEndpoint() {
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURI()).thenReturn("/api/urls");
    assertThat(filter.shouldNotFilter(request)).isTrue();

    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/api/urls");
    assertThat(filter.shouldNotFilter(request)).isFalse();

    when(request.getMethod()).thenReturn("POST");
    when(request.getRequestURI()).thenReturn("/other");
    assertThat(filter.shouldNotFilter(request)).isTrue();
  }

  @Test
  void allowsRequestsWithinCapacity() throws Exception {
    when(request.getRemoteAddr()).thenReturn("10.0.0.1");
    lenient().when(request.getHeader("X-Forwarded-For")).thenReturn(null);

    filter.doFilterInternal(request, response, chain);
    filter.doFilterInternal(request, response, chain);

    verify(chain, times(2)).doFilter(request, response);
    verify(response, never()).setStatus(429);
  }

  @Test
  void blocksRequestsBeyondCapacityWith429() throws Exception {
    when(request.getRemoteAddr()).thenReturn("10.0.0.2");
    lenient().when(request.getHeader("X-Forwarded-For")).thenReturn(null);
    StringWriter body = new StringWriter();
    when(response.getWriter()).thenReturn(new PrintWriter(body));

    filter.doFilterInternal(request, response, chain); // 1
    filter.doFilterInternal(request, response, chain); // 2 (capacity)
    filter.doFilterInternal(request, response, chain); // 3 (over capacity)

    verify(chain, times(2)).doFilter(any(), any());
    verify(response).setStatus(429);
    assertThat(body.toString()).contains("Rate limit exceeded");
  }

  @Test
  void tracksBucketsSeparatelyPerClientIp() throws Exception {
    when(request.getRemoteAddr()).thenReturn("10.0.0.3", "10.0.0.3", "10.0.0.4");
    lenient().when(request.getHeader("X-Forwarded-For")).thenReturn(null);

    filter.doFilterInternal(request, response, chain); // client A, 1
    filter.doFilterInternal(request, response, chain); // client A, 2 (capacity reached)
    filter.doFilterInternal(request, response, chain); // client B, 1 (fresh bucket)

    verify(chain, times(3)).doFilter(any(), any());
    verify(response, never()).setStatus(429);
  }
}
