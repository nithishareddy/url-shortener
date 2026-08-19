package com.schwab.urlshortener.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HardeningFlowIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void customAliasIsHonored() throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new CreateRequest("https://example.com/aliased", "my-alias", null));

    mockMvc
        .perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());

    mockMvc.perform(get("/{shortCode}", "my-alias")).andExpect(status().isFound());
  }

  @Test
  void duplicateAliasIsRejected() throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new CreateRequest("https://example.com/one", "dupe-alias", null));
    mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(body));

    String again =
        objectMapper.writeValueAsString(
            new CreateRequest("https://example.com/two", "dupe-alias", null));
    mockMvc
        .perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(again))
        .andExpect(status().isConflict());
  }

  @Test
  void reservedAliasIsRejected() throws Exception {
    String body =
        objectMapper.writeValueAsString(new CreateRequest("https://example.com/x", "health", null));
    mockMvc
        .perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void loopbackTargetIsRejectedAsUnsafe() throws Exception {
    String body =
        objectMapper.writeValueAsString(new CreateRequest("http://127.0.0.1/admin", null, null));
    mockMvc
        .perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void expiredLinkReturnsGone() throws Exception {
    Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
    String body =
        objectMapper.writeValueAsString(
            new CreateRequest("https://example.com/expired", "already-expired", past));

    mockMvc
        .perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());

    mockMvc.perform(get("/{shortCode}", "already-expired")).andExpect(status().isGone());
  }

  @Test
  void deactivatedLinkReturnsGone() throws Exception {
    String body =
        objectMapper.writeValueAsString(
            new CreateRequest("https://example.com/to-deactivate", "to-kill", null));
    mockMvc.perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(body));

    mockMvc.perform(delete("/api/urls/{shortCode}", "to-kill")).andExpect(status().isNoContent());
    mockMvc.perform(get("/{shortCode}", "to-kill")).andExpect(status().isGone());
  }

  private record CreateRequest(String longUrl, String customAlias, Instant expiresAt) {}
}
