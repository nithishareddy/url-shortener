package com.schwab.urlshortener.dto;

import java.time.LocalDate;
import java.util.List;

public record AnalyticsResponse(
    String shortCode,
    long totalClicks,
    List<DailyCount> clicksByDay,
    List<ReferrerCount> topReferrers) {

  public record DailyCount(LocalDate date, long count) {}

  public record ReferrerCount(String referrer, long count) {}
}
