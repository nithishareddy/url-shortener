package com.schwab.urlshortener.config;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Click recording runs off the redirect request thread (see ClickTrackingService) so analytics
 * writes never add latency to the 302 response. Bounded pool + queue so a burst of clicks can't
 * exhaust memory; clicks are best-effort (see docs/SCENARIOS.md for the delivery trade-off).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

  @Bean(name = "clickTrackingExecutor")
  public Executor clickTrackingExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(1000);
    executor.setThreadNamePrefix("click-tracking-");
    executor.initialize();
    return executor;
  }
}
