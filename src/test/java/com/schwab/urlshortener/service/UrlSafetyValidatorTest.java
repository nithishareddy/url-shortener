package com.schwab.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UrlSafetyValidatorTest {

  private final UrlSafetyValidator validator = new UrlSafetyValidator();

  @Test
  void acceptsOrdinaryHttpsUrl() {
    assertThat(validator.validate("https://example.com/path").valid()).isTrue();
  }

  @Test
  void rejectsNonHttpScheme() {
    assertThat(validator.validate("ftp://example.com/file").valid()).isFalse();
  }

  @Test
  void rejectsMalformedUrl() {
    assertThat(validator.validate("not a url").valid()).isFalse();
  }

  @Test
  void rejectsLoopbackTarget() {
    assertThat(validator.validate("http://127.0.0.1/admin").valid()).isFalse();
    assertThat(validator.validate("http://localhost/admin").valid()).isFalse();
  }

  @Test
  void rejectsPrivateNetworkTarget() {
    assertThat(validator.validate("http://10.0.0.5/internal").valid()).isFalse();
    assertThat(validator.validate("http://192.168.1.1/router").valid()).isFalse();
  }

  @Test
  void rejectsLinkLocalMetadataEndpoint() {
    assertThat(validator.validate("http://169.254.169.254/latest/meta-data").valid()).isFalse();
  }
}
