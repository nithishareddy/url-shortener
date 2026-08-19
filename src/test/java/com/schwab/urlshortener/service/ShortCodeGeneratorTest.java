package com.schwab.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShortCodeGeneratorTest {

  private final ShortCodeGenerator generator = new ShortCodeGenerator();

  @Test
  void encodesToAtLeastSixCharacters() {
    assertThat(generator.encode(1).length()).isGreaterThanOrEqualTo(6);
    assertThat(generator.encode(1_000_000_000L).length()).isGreaterThanOrEqualTo(6);
  }

  @Test
  void isDeterministic() {
    assertThat(generator.encode(42)).isEqualTo(generator.encode(42));
  }

  @Test
  void producesDistinctCodesForDistinctIds() {
    Set<String> codes = new HashSet<>();
    for (long id = 1; id <= 10_000; id++) {
      codes.add(generator.encode(id));
    }
    assertThat(codes).hasSize(10_000);
  }

  @Test
  void producesUrlSafeCharactersOnly() {
    String code = generator.encode(123456789L);
    assertThat(code).matches("^[0-9a-zA-Z]+$");
  }
}
