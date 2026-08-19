package com.schwab.urlshortener.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "short_url")
public class ShortUrl {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "short_code", unique = true, length = 20)
  private String shortCode;

  @Column(name = "long_url", nullable = false, length = 2048)
  private String longUrl;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ShortUrl() {}

  public ShortUrl(String longUrl) {
    this.longUrl = longUrl;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getShortCode() {
    return shortCode;
  }

  public void setShortCode(String shortCode) {
    this.shortCode = shortCode;
  }

  public String getLongUrl() {
    return longUrl;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
