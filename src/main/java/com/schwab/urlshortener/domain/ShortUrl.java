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

  @Column(name = "custom_alias", nullable = false)
  private boolean customAlias;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "active", nullable = false)
  private boolean active = true;

  protected ShortUrl() {}

  public ShortUrl(String longUrl) {
    this(longUrl, false, null);
  }

  public ShortUrl(String longUrl, boolean customAlias, Instant expiresAt) {
    this.longUrl = longUrl;
    this.customAlias = customAlias;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
    this.active = true;
  }

  public boolean isExpired() {
    return expiresAt != null && Instant.now().isAfter(expiresAt);
  }

  public boolean isRedirectable() {
    return active && !isExpired();
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

  public boolean isCustomAlias() {
    return customAlias;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public boolean isActive() {
    return active;
  }

  public void deactivate() {
    this.active = false;
  }
}
