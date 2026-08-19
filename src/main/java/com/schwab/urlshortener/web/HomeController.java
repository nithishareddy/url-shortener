package com.schwab.urlshortener.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the demo page at bare "/".
 *
 * <p>Found during manual smoke-testing: RedirectController's {@code /{shortCode}} pattern is
 * registered in the same (higher-priority) handler mapping as Spring's static-resource handler, so
 * without an exact-literal "/" mapping here, requests for "/" were being swallowed as a lookup for
 * a short code literally named "index.html" (via Spring Boot's welcome-page forward) instead of
 * serving the page. An exact match ranks above a path-variable match within the same handler
 * mapping, so this wins over the redirect route; the resource is read directly rather than
 * forwarded, to avoid re-entering the same routing collision.
 */
@RestController
public class HomeController {

  @GetMapping("/")
  public ResponseEntity<Resource> home() {
    return ResponseEntity.ok()
        .contentType(MediaType.TEXT_HTML)
        .body(new ClassPathResource("static/index.html"));
  }
}
