package com.schwab.urlshortener.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schwab.urlshortener.config.AppProperties;
import com.schwab.urlshortener.domain.ShortUrl;
import com.schwab.urlshortener.exception.AliasConflictException;
import com.schwab.urlshortener.exception.InvalidAliasException;
import com.schwab.urlshortener.exception.ShortUrlNotFoundException;
import com.schwab.urlshortener.exception.UnsafeUrlException;
import com.schwab.urlshortener.service.ShortUrlService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * Standalone MockMvc: no Spring context is bootstrapped, no DataSource exists, and the service
 * layer is a Mockito mock — this exercises only the controller + validation + exception-mapping
 * layer, never a database.
 */
@ExtendWith(MockitoExtension.class)
class ShortUrlControllerTest {

  @Mock private ShortUrlService shortUrlService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    AppProperties appProperties = new AppProperties("http://localhost:8080", null, List.of());
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ShortUrlController(shortUrlService, appProperties))
            .setControllerAdvice(new GlobalExceptionHandler())
            .setValidator(new LocalValidatorFactoryBean())
            .build();
  }

  @Test
  void createReturns201WithShortUrl() throws Exception {
    ShortUrl created = new ShortUrl("https://example.com/x");
    created.setShortCode("abc123");
    when(shortUrlService.create(any())).thenReturn(created);

    mockMvc
        .perform(
            post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"longUrl\":\"https://example.com/x\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.shortCode").value("abc123"))
        .andExpect(jsonPath("$.shortUrl").value("http://localhost:8080/abc123"));
  }

  @Test
  void createRejectsBlankLongUrl() throws Exception {
    mockMvc
        .perform(
            post("/api/urls").contentType(MediaType.APPLICATION_JSON).content("{\"longUrl\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createMapsUnsafeUrlExceptionTo400() throws Exception {
    when(shortUrlService.create(any())).thenThrow(new UnsafeUrlException("blocked"));

    mockMvc
        .perform(
            post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"longUrl\":\"http://127.0.0.1/admin\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createMapsReservedAliasExceptionTo400() throws Exception {
    when(shortUrlService.create(any())).thenThrow(new InvalidAliasException("reserved"));

    mockMvc
        .perform(
            post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"longUrl\":\"https://example.com/x\",\"customAlias\":\"health\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createMapsAliasConflictTo409() throws Exception {
    when(shortUrlService.create(any())).thenThrow(new AliasConflictException("taken"));

    mockMvc
        .perform(
            post("/api/urls")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"longUrl\":\"https://example.com/x\",\"customAlias\":\"taken\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void getMetadataReturns200ForKnownCode() throws Exception {
    when(shortUrlService.getMetadata("abc123")).thenReturn(new ShortUrl("https://example.com/x"));

    mockMvc.perform(get("/api/urls/{shortCode}", "abc123")).andExpect(status().isOk());
  }

  @Test
  void getMetadataReturns404ForUnknownCode() throws Exception {
    when(shortUrlService.getMetadata("missing"))
        .thenThrow(new ShortUrlNotFoundException("missing"));

    mockMvc.perform(get("/api/urls/{shortCode}", "missing")).andExpect(status().isNotFound());
  }

  @Test
  void deactivateReturns204() throws Exception {
    mockMvc.perform(delete("/api/urls/{shortCode}", "abc123")).andExpect(status().isNoContent());
  }

  @Test
  void deactivateReturns404ForUnknownCode() throws Exception {
    doThrow(new ShortUrlNotFoundException("missing")).when(shortUrlService).deactivate("missing");

    mockMvc.perform(delete("/api/urls/{shortCode}", "missing")).andExpect(status().isNotFound());
  }
}
