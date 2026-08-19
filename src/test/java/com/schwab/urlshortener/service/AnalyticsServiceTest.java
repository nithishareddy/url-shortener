package com.schwab.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.schwab.urlshortener.domain.ShortUrl;
import com.schwab.urlshortener.exception.ShortUrlNotFoundException;
import com.schwab.urlshortener.repository.ClickEventRepository;
import com.schwab.urlshortener.repository.ShortUrlRepository;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/** Both repositories are mocked; no test here executes the real native aggregation SQL. */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

  @Mock private ShortUrlRepository shortUrlRepository;
  @Mock private ClickEventRepository clickEventRepository;

  private AnalyticsService newService() {
    return new AnalyticsService(shortUrlRepository, clickEventRepository);
  }

  @Test
  void assemblesAnalyticsFromRepositoryRows() {
    ShortUrl shortUrl = new ShortUrl("https://example.com/x");
    ReflectionTestUtils.setField(shortUrl, "id", 7L);
    when(shortUrlRepository.findByShortCode("abc123")).thenReturn(Optional.of(shortUrl));
    when(clickEventRepository.countByShortUrlId(7L)).thenReturn(3L);
    when(clickEventRepository.countByDaySince(eq(7L), any(Instant.class)))
        .thenReturn(List.<Object[]>of(new Object[] {Date.valueOf(LocalDate.of(2026, 8, 19)), 3L}));
    when(clickEventRepository.topReferrers(anyLong(), anyInt()))
        .thenReturn(
            List.<Object[]>of(
                new Object[] {"https://google.com", 2L}, new Object[] {"direct", 1L}));

    var result = newService().getAnalytics("abc123");

    assertThat(result.shortCode()).isEqualTo("abc123");
    assertThat(result.totalClicks()).isEqualTo(3L);
    assertThat(result.clicksByDay()).hasSize(1);
    assertThat(result.clicksByDay().get(0).date()).isEqualTo(LocalDate.of(2026, 8, 19));
    assertThat(result.clicksByDay().get(0).count()).isEqualTo(3L);
    assertThat(result.topReferrers()).hasSize(2);
    assertThat(result.topReferrers().get(0).referrer()).isEqualTo("https://google.com");
    assertThat(result.topReferrers().get(0).count()).isEqualTo(2L);
  }

  @Test
  void throwsNotFoundForUnknownCode() {
    when(shortUrlRepository.findByShortCode("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> newService().getAnalytics("missing"))
        .isInstanceOf(ShortUrlNotFoundException.class);
  }
}
