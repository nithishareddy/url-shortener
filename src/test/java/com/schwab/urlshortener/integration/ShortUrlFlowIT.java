package com.schwab.urlshortener.integration;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class ShortUrlFlowIT {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void createThenRedirect() throws Exception {
    String body =
        objectMapper.writeValueAsString(new CreateRequest("https://example.com/some/long/path"));

    String shortCode =
        objectMapper
            .readTree(
                mockMvc
                    .perform(
                        post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.shortUrl").value(startsWith("http://localhost:8080/")))
                    .andReturn()
                    .getResponse()
                    .getContentAsString())
            .get("shortCode")
            .asText();

    mockMvc
        .perform(get("/{shortCode}", shortCode))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/some/long/path"));
  }

  @Test
  void createRejectsBlankUrl() throws Exception {
    String body = objectMapper.writeValueAsString(new CreateRequest(""));

    mockMvc
        .perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createRejectsNonHttpScheme() throws Exception {
    String body = objectMapper.writeValueAsString(new CreateRequest("ftp://example.com/file"));

    mockMvc
        .perform(post("/api/urls").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void redirectForUnknownCodeReturns404() throws Exception {
    mockMvc.perform(get("/{shortCode}", "doesNotExist")).andExpect(status().isNotFound());
  }

  @Test
  void rootServesDemoPageInsteadOfBeingSwallowedByRedirectRoute() throws Exception {
    // Regression test: the "/{shortCode}" pattern used to shadow static resource resolution for
    // "/", making the redirect route 404 on a bogus "index.html" short code. See HomeController.
    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", startsWith(MediaType.TEXT_HTML_VALUE)));
  }

  private record CreateRequest(String longUrl) {}
}
