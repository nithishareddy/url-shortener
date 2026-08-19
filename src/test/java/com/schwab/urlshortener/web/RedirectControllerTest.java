package com.schwab.urlshortener.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schwab.urlshortener.domain.ShortUrl;
import com.schwab.urlshortener.exception.ShortUrlGoneException;
import com.schwab.urlshortener.exception.ShortUrlNotFoundException;
import com.schwab.urlshortener.service.ClickTrackingService;
import com.schwab.urlshortener.service.ShortUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class RedirectControllerTest {

  @Mock private ShortUrlService shortUrlService;
  @Mock private ClickTrackingService clickTrackingService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new RedirectController(shortUrlService, clickTrackingService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void redirectsToLongUrlAndRecordsClick() throws Exception {
    ShortUrl shortUrl = new ShortUrl("https://example.com/target");
    org.springframework.test.util.ReflectionTestUtils.setField(shortUrl, "id", 7L);
    when(shortUrlService.resolveForRedirect("abc123")).thenReturn(shortUrl);

    mockMvc
        .perform(get("/{shortCode}", "abc123").header(HttpHeaders.REFERER, "https://google.com"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/target"));

    verify(clickTrackingService).recordClick(eq(7L), eq("https://google.com"), any());
  }

  @Test
  void redirectRecordsClickWithNullHeadersWhenAbsent() throws Exception {
    ShortUrl shortUrl = new ShortUrl("https://example.com/target");
    org.springframework.test.util.ReflectionTestUtils.setField(shortUrl, "id", 7L);
    when(shortUrlService.resolveForRedirect("abc123")).thenReturn(shortUrl);

    mockMvc.perform(get("/{shortCode}", "abc123")).andExpect(status().isFound());

    verify(clickTrackingService).recordClick(eq(7L), isNull(), isNull());
  }

  @Test
  void unknownCodeReturns404() throws Exception {
    when(shortUrlService.resolveForRedirect("missing"))
        .thenThrow(new ShortUrlNotFoundException("missing"));

    mockMvc.perform(get("/{shortCode}", "missing")).andExpect(status().isNotFound());
  }

  @Test
  void expiredOrDeactivatedLinkReturns410() throws Exception {
    when(shortUrlService.resolveForRedirect("gone")).thenThrow(new ShortUrlGoneException("gone"));

    mockMvc.perform(get("/{shortCode}", "gone")).andExpect(status().isGone());
  }
}
