package com.schwab.urlshortener.web;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schwab.urlshortener.dto.AnalyticsResponse;
import com.schwab.urlshortener.exception.ShortUrlNotFoundException;
import com.schwab.urlshortener.service.AnalyticsService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerTest {

  @Mock private AnalyticsService analyticsService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new AnalyticsController(analyticsService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void returnsAnalyticsForKnownCode() throws Exception {
    when(analyticsService.getAnalytics("abc123"))
        .thenReturn(new AnalyticsResponse("abc123", 3, List.of(), List.of()));

    mockMvc
        .perform(get("/api/urls/{shortCode}/analytics", "abc123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalClicks").value(3));
  }

  @Test
  void unknownCodeReturns404() throws Exception {
    when(analyticsService.getAnalytics("missing"))
        .thenThrow(new ShortUrlNotFoundException("missing"));

    mockMvc
        .perform(get("/api/urls/{shortCode}/analytics", "missing"))
        .andExpect(status().isNotFound());
  }
}
