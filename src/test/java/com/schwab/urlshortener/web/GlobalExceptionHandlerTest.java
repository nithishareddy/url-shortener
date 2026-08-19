package com.schwab.urlshortener.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.schwab.urlshortener.exception.ShortUrlGoneException;
import com.schwab.urlshortener.exception.ShortUrlNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Direct unit tests for handler branches that standalone MockMvc (used by the controller tests)
 * can't naturally trigger — {@link NoResourceFoundException} is only ever thrown by a full
 * DispatcherServlet + static-resource-handler setup, not a bare standalone controller.
 */
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void mapsNoResourceFoundTo404NotAServerError() {
    // Regression coverage for the bug fixed in HomeController's introduction: an unmatched route
    // (e.g. a malformed URL) must be a quiet 404, not a 500 from the catch-all handler.
    var result =
        handler.handleNoRouteMatched(new NoResourceFoundException(HttpMethod.GET, "/bogus/path"));

    assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  @Test
  void mapsShortUrlNotFoundTo404() {
    var result = handler.handleNotFound(new ShortUrlNotFoundException("abc123"));

    assertThat(result.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
  }

  @Test
  void mapsShortUrlGoneTo410() {
    var result = handler.handleGone(new ShortUrlGoneException("abc123"));

    assertThat(result.getStatus()).isEqualTo(HttpStatus.GONE.value());
  }

  @Test
  void mapsUnexpectedExceptionTo500WithoutLeakingDetails() {
    var result = handler.handleUnexpected(new RuntimeException("some internal detail"));

    assertThat(result.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(result.getDetail()).doesNotContain("some internal detail");
  }
}
