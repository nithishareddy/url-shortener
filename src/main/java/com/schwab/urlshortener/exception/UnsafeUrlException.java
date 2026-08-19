package com.schwab.urlshortener.exception;

public class UnsafeUrlException extends RuntimeException {
  public UnsafeUrlException(String message) {
    super(message);
  }
}
