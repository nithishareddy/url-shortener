package com.schwab.urlshortener;

import com.schwab.urlshortener.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class UrlShortenerApplication {

  public static void main(String[] args) {
    SpringApplication.run(UrlShortenerApplication.class, args);
  }
}
