package com.schwab.urlshortener.service;

import com.schwab.urlshortener.domain.ClickEvent;
import com.schwab.urlshortener.repository.ClickEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ClickTrackingService {

  private static final Logger log = LoggerFactory.getLogger(ClickTrackingService.class);

  private final ClickEventRepository clickEventRepository;

  public ClickTrackingService(ClickEventRepository clickEventRepository) {
    this.clickEventRepository = clickEventRepository;
  }

  @Async("clickTrackingExecutor")
  public void recordClick(Long shortUrlId, String referrer, String userAgent) {
    try {
      clickEventRepository.save(new ClickEvent(shortUrlId, referrer, userAgent));
    } catch (Exception e) {
      // Analytics is best-effort: a failed click write must never surface as a redirect failure.
      log.warn("Failed to record click for shortUrlId={}", shortUrlId, e);
    }
  }
}
