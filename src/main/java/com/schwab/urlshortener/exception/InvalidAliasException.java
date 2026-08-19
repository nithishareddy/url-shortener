package com.schwab.urlshortener.exception;

public class InvalidAliasException extends RuntimeException {
  public InvalidAliasException(String message) {
    super(message);
  }
}
