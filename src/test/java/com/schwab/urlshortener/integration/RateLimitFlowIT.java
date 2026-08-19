package com.schwab.urlshortener.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = {"app.rate-limit.create.capacity=2", "app.rate-limit.create.refill-tokens=2"})
class RateLimitFlowIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void exceedingCreateLimitReturns429() throws Exception {
    String body = objectMapper.writeValueAsString(new CreateRequest("https://example.com/rl"));

    mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(body));
    mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(body));

    mockMvc
        .perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isTooManyRequests());
  }

  private record CreateRequest(String longUrl) {}
}
