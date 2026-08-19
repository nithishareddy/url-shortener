package com.schwab.urlshortener.web;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.schwab.urlshortener.domain.ShortUrl;
import com.schwab.urlshortener.service.ClickTrackingService;
import com.schwab.urlshortener.service.ShortUrlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * HomeControllerTest and RedirectControllerTest each test one controller in isolation, which can't
 * reproduce the routing collision documented in docs/SCENARIOS.md (Scenario 3, Bug 3):
 * RedirectController's "/{shortCode}" pattern only shadows "/" when BOTH controllers share one
 * routing table. This registers both together — still no Spring context, no DB, just two real
 * controllers under one MockMvc dispatcher — to keep that regression covered by an automated test.
 */
@ExtendWith(MockitoExtension.class)
class RoutingTest {

  @Mock private ShortUrlService shortUrlService;
  @Mock private ClickTrackingService clickTrackingService;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new HomeController(), new RedirectController(shortUrlService, clickTrackingService))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void rootPathServesDemoPageRatherThanBeingClaimedByRedirectRoute() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", startsWith(MediaType.TEXT_HTML_VALUE)));
  }

  @Test
  void realShortCodeStillRedirectsWithBothControllersRegistered() throws Exception {
    ShortUrl shortUrl = new ShortUrl("https://example.com/target");
    ReflectionTestUtils.setField(shortUrl, "id", 1L);
    when(shortUrlService.resolveForRedirect("abc123")).thenReturn(shortUrl);

    mockMvc
        .perform(get("/{shortCode}", "abc123"))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "https://example.com/target"));
  }
}
