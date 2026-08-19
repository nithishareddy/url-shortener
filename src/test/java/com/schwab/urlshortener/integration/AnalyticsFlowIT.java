package com.schwab.urlshortener.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Click recording is async in production (see ClickTrackingService) so the redirect response isn't
 * delayed by a DB write. Here the executor is swapped for a synchronous one so the click is
 * guaranteed to be persisted before the analytics assertions run, without resorting to polling.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class AnalyticsFlowIT {

  @TestConfiguration
  static class SyncExecutorConfig {
    @Bean(name = "clickTrackingExecutor")
    @Primary
    Executor clickTrackingExecutor() {
      return new SyncTaskExecutor();
    }
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void redirectsAreReflectedInAnalytics() throws Exception {
    String body =
        objectMapper.writeValueAsString(new CreateRequest("https://example.com/analytics-target"));

    String shortCode =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("shortCode")
            .asText();

    mockMvc.perform(
        get("/{shortCode}", shortCode).header(HttpHeaders.REFERER, "https://google.com"));
    mockMvc.perform(
        get("/{shortCode}", shortCode).header(HttpHeaders.REFERER, "https://google.com"));
    mockMvc.perform(get("/{shortCode}", shortCode));

    mockMvc
        .perform(get("/api/urls/{shortCode}/analytics", shortCode))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalClicks").value(3))
        .andExpect(jsonPath("$.topReferrers[0].referrer").value("https://google.com"))
        .andExpect(jsonPath("$.topReferrers[0].count").value(2));
  }

  @Test
  void analyticsForUnknownCodeReturns404() throws Exception {
    mockMvc
        .perform(get("/api/urls/{shortCode}/analytics", "doesNotExist"))
        .andExpect(status().isNotFound());
  }

  private record CreateRequest(String longUrl) {}
}
