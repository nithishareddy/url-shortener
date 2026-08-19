package com.schwab.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "click_event")
public class ClickEvent {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "short_url_id", nullable = false)
  private Long shortUrlId;

  @Column(name = "clicked_at", nullable = false)
  private Instant clickedAt;

  @Column(name = "referrer", length = 512)
  private String referrer;

  @Column(name = "user_agent", length = 512)
  private String userAgent;

  protected ClickEvent() {}

  public ClickEvent(Long shortUrlId, String referrer, String userAgent) {
    this.shortUrlId = shortUrlId;
    this.clickedAt = Instant.now();
    this.referrer = truncate(referrer);
    this.userAgent = truncate(userAgent);
  }

  private static String truncate(String value) {
    if (value == null) {
      return null;
    }
    return value.length() > 512 ? value.substring(0, 512) : value;
  }

  public Long getId() {
    return id;
  }

  public Long getShortUrlId() {
    return shortUrlId;
  }

  public Instant getClickedAt() {
    return clickedAt;
  }

  public String getReferrer() {
    return referrer;
  }

  public String getUserAgent() {
    return userAgent;
  }
}
