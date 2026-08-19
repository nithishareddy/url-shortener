package com.schwab.urlshortener.repository;

import com.schwab.urlshortener.domain.ClickEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClickEventRepository extends JpaRepository<ClickEvent, Long> {

  long countByShortUrlId(Long shortUrlId);

  @Query(
      value =
          "SELECT CAST(clicked_at AS DATE) AS click_day, COUNT(*) AS click_count "
              + "FROM click_event WHERE short_url_id = :shortUrlId AND clicked_at >= :since "
              + "GROUP BY CAST(clicked_at AS DATE) ORDER BY click_day",
      nativeQuery = true)
  List<Object[]> countByDaySince(
      @Param("shortUrlId") Long shortUrlId, @Param("since") Instant since);

  @Query(
      value =
          "SELECT COALESCE(referrer, 'direct') AS ref, COUNT(*) AS click_count "
              + "FROM click_event WHERE short_url_id = :shortUrlId "
              + "GROUP BY COALESCE(referrer, 'direct') ORDER BY click_count DESC LIMIT :limit",
      nativeQuery = true)
  List<Object[]> topReferrers(@Param("shortUrlId") Long shortUrlId, @Param("limit") int limit);
}
