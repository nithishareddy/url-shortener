package com.schwab.urlshortener.exception;

public class ShortUrlNotFoundException extends RuntimeException {
  public ShortUrlNotFoundException(String shortCode) {
    super("No short URL found for code '" + shortCode + "'");
  }
}
