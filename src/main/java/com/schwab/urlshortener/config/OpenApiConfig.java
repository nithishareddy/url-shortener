package com.schwab.urlshortener.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI urlShortenerOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("URL Shortener API")
                .version("v1")
                .description("Core shortening, redirect, and analytics APIs."));
  }
}
