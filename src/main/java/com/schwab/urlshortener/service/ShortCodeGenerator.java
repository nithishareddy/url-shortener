package com.schwab.urlshortener.service;

import org.springframework.stereotype.Component;

/**
 * Base62-encodes a database-generated numeric id into a short, URL-safe code.
 *
 * <p>Deterministic and collision-free by construction (each id maps to exactly one code), unlike
 * random-string generation which needs a collision-retry loop. An offset is added so early ids
 * don't produce single-character codes.
 */
@Component
public class ShortCodeGenerator {

  private static final String ALPHABET =
      "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static final int BASE = ALPHABET.length();

  // Pushes the smallest generated codes to 6 characters instead of 1, matching
  // app.short-code.min-length.
  private static final long OFFSET = 56_800_235_584L; // 62^6

  public String encode(long id) {
    long value = id + OFFSET;
    StringBuilder sb = new StringBuilder();
    while (value > 0) {
      sb.append(ALPHABET.charAt((int) (value % BASE)));
      value /= BASE;
    }
    return sb.reverse().toString();
  }
}
