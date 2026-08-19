package com.schwab.urlshortener.web;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HomeControllerTest {

  private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HomeController()).build();

  @Test
  void rootServesTheDemoPage() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", startsWith(MediaType.TEXT_HTML_VALUE)))
        .andExpect(content().string(startsWith("<!doctype html>")));
  }
}
