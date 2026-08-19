package com.schwab.urlshortener.service;

import com.schwab.urlshortener.domain.ShortUrl;
import com.schwab.urlshortener.dto.AnalyticsResponse;
import com.schwab.urlshortener.dto.AnalyticsResponse.DailyCount;
import com.schwab.urlshortener.dto.AnalyticsResponse.ReferrerCount;
import com.schwab.urlshortener.exception.ShortUrlNotFoundException;
import com.schwab.urlshortener.repository.ClickEventRepository;
import com.schwab.urlshortener.repository.ShortUrlRepository;
import java.sql.Date;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {

  // Bounds the daily-breakdown window; a longer history is a rollup/retention concern deferred to
  // a later iteration (see docs/SCENARIOS.md).
  private static final int DAILY_WINDOW_DAYS = 30;
  private static final int TOP_REFERRERS_LIMIT = 5;

  private final ShortUrlRepository shortUrlRepository;
  private final ClickEventRepository clickEventRepository;

  public AnalyticsService(
      ShortUrlRepository shortUrlRepository, ClickEventRepository clickEventRepository) {
    this.shortUrlRepository = shortUrlRepository;
    this.clickEventRepository = clickEventRepository;
  }

  @Transactional(readOnly = true)
  public AnalyticsResponse getAnalytics(String shortCode) {
    ShortUrl shortUrl =
        shortUrlRepository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

    long total = clickEventRepository.countByShortUrlId(shortUrl.getId());

    Instant since = Instant.now().minus(DAILY_WINDOW_DAYS, ChronoUnit.DAYS);
    List<DailyCount> clicksByDay =
        clickEventRepository.countByDaySince(shortUrl.getId(), since).stream()
            .map(
                row -> new DailyCount(((Date) row[0]).toLocalDate(), ((Number) row[1]).longValue()))
            .toList();

    List<ReferrerCount> topReferrers =
        clickEventRepository.topReferrers(shortUrl.getId(), TOP_REFERRERS_LIMIT).stream()
            .map(row -> new ReferrerCount((String) row[0], ((Number) row[1]).longValue()))
            .toList();

    return new AnalyticsResponse(shortCode, total, clicksByDay, topReferrers);
  }
}
