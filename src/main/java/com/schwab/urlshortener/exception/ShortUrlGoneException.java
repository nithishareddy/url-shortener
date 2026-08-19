package com.schwab.urlshortener.exception;

public class ShortUrlGoneException extends RuntimeException {
  public ShortUrlGoneException(String shortCode) {
    super("Short URL '" + shortCode + "' has expired or been deactivated");
  }
}
