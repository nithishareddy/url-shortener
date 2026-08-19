package com.schwab.urlshortener.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.schwab.urlshortener.config.AppProperties;
import com.schwab.urlshortener.domain.ShortUrl;
import com.schwab.urlshortener.dto.CreateShortUrlRequest;
import com.schwab.urlshortener.exception.AliasConflictException;
import com.schwab.urlshortener.exception.InvalidAliasException;
import com.schwab.urlshortener.exception.ShortUrlGoneException;
import com.schwab.urlshortener.exception.ShortUrlNotFoundException;
import com.schwab.urlshortener.exception.UnsafeUrlException;
import com.schwab.urlshortener.repository.ShortUrlRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pure unit tests: the repository is mocked, so nothing here ever opens a database connection. See
 * docs/TESTING.md for the trade-off this accepts (no test executes the real Flyway schema, native
 * SQL, or DB unique-constraint behavior any more).
 */
@ExtendWith(MockitoExtension.class)
class ShortUrlServiceTest {

  @Mock private ShortUrlRepository repository;
  @Mock private UrlSafetyValidator safetyValidator;

  private final ShortCodeGenerator codeGenerator = new ShortCodeGenerator();
  private ShortUrlService service;

  @BeforeEach
  void setUp() {
    AppProperties appProperties =
        new AppProperties(
            "http://localhost:8080",
            new AppProperties.RateLimit(new AppProperties.RateLimit.Create(20, 20, 60)),
            List.of("api", "health", "actuator"));
    service = new ShortUrlService(repository, codeGenerator, safetyValidator, appProperties);
  }

  private void stubSafeUrl() {
    when(safetyValidator.validate(any()))
        .thenReturn(new UrlSafetyValidator.ValidationResult(true, null));
  }

  // saveAndFlush's IDENTITY generation is simulated by setting the id via reflection, mirroring
  // what Hibernate would do on a real insert — this is the one place a real DB round-trip is
  // stood in for, since ShortUrl.id has no public setter (by design; only Hibernate assigns it).
  private void stubGeneratedId(long id) {
    doAnswer(
            invocation -> {
              ShortUrl arg = invocation.getArgument(0);
              ReflectionTestUtils.setField(arg, "id", id);
              return arg;
            })
        .when(repository)
        .saveAndFlush(any());
  }

  @Test
  void createWithGeneratedCodeDerivesShortCodeFromId() {
    stubSafeUrl();
    stubGeneratedId(1_000_000L);

    ShortUrl result =
        service.create(new CreateShortUrlRequest("https://example.com/path", null, null));

    assertThat(result.getShortCode()).isEqualTo(codeGenerator.encode(1_000_000L));
    assertThat(result.isCustomAlias()).isFalse();
  }

  @Test
  void createRejectsUnsafeUrl() {
    when(safetyValidator.validate(any()))
        .thenReturn(
            new UrlSafetyValidator.ValidationResult(
                false, "URL host resolves to a private address"));

    assertThatThrownBy(
            () -> service.create(new CreateShortUrlRequest("http://127.0.0.1/admin", null, null)))
        .isInstanceOf(UnsafeUrlException.class);

    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void createWithCustomAliasSucceeds() {
    stubSafeUrl();
    when(repository.existsByShortCode("my-alias")).thenReturn(false);
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

    ShortUrl result =
        service.create(new CreateShortUrlRequest("https://example.com/x", "my-alias", null));

    assertThat(result.getShortCode()).isEqualTo("my-alias");
    assertThat(result.isCustomAlias()).isTrue();
  }

  @Test
  void createRejectsReservedAlias() {
    stubSafeUrl();

    assertThatThrownBy(
            () ->
                service.create(new CreateShortUrlRequest("https://example.com/x", "health", null)))
        .isInstanceOf(InvalidAliasException.class);

    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void createRejectsAliasAlreadyClaimed() {
    stubSafeUrl();
    when(repository.existsByShortCode("taken")).thenReturn(true);

    assertThatThrownBy(
            () -> service.create(new CreateShortUrlRequest("https://example.com/x", "taken", null)))
        .isInstanceOf(AliasConflictException.class);

    verify(repository, never()).saveAndFlush(any());
  }

  @Test
  void createHandlesConcurrentAliasRaceViaUniqueConstraintViolation() {
    // The pre-check (existsByShortCode) says the alias is free, but a concurrent request claims
    // it first and the DB's unique constraint rejects the insert. This exercises the catch block
    // in ShortUrlService#createWithCustomAlias that has no other route to test, since a genuine
    // race is hard to trigger deterministically against a real single-threaded DB test.
    stubSafeUrl();
    when(repository.existsByShortCode("racy-alias")).thenReturn(false);
    when(repository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("unique constraint"));

    assertThatThrownBy(
            () ->
                service.create(
                    new CreateShortUrlRequest("https://example.com/x", "racy-alias", null)))
        .isInstanceOf(AliasConflictException.class);
  }

  @Test
  void resolveForRedirectReturnsActiveLink() {
    ShortUrl shortUrl = new ShortUrl("https://example.com/x");
    when(repository.findByShortCode("abc123")).thenReturn(Optional.of(shortUrl));

    assertThat(service.resolveForRedirect("abc123")).isSameAs(shortUrl);
  }

  @Test
  void resolveForRedirectThrowsNotFoundForUnknownCode() {
    when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.resolveForRedirect("missing"))
        .isInstanceOf(ShortUrlNotFoundException.class);
  }

  @Test
  void resolveForRedirectThrowsGoneForExpiredLink() {
    ShortUrl expired =
        new ShortUrl("https://example.com/x", false, Instant.now().minus(1, ChronoUnit.DAYS));
    when(repository.findByShortCode("expired")).thenReturn(Optional.of(expired));

    assertThatThrownBy(() -> service.resolveForRedirect("expired"))
        .isInstanceOf(ShortUrlGoneException.class);
  }

  @Test
  void resolveForRedirectThrowsGoneForDeactivatedLink() {
    ShortUrl deactivated = new ShortUrl("https://example.com/x");
    deactivated.deactivate();
    when(repository.findByShortCode("gone")).thenReturn(Optional.of(deactivated));

    assertThatThrownBy(() -> service.resolveForRedirect("gone"))
        .isInstanceOf(ShortUrlGoneException.class);
  }

  @Test
  void getMetadataThrowsNotFoundForUnknownCode() {
    when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getMetadata("missing"))
        .isInstanceOf(ShortUrlNotFoundException.class);
  }

  @Test
  void deactivateMarksLinkInactive() {
    ShortUrl shortUrl = new ShortUrl("https://example.com/x");
    when(repository.findByShortCode("abc123")).thenReturn(Optional.of(shortUrl));

    service.deactivate("abc123");

    assertThat(shortUrl.isActive()).isFalse();
  }

  @Test
  void deactivateThrowsNotFoundForUnknownCode() {
    when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.deactivate("missing"))
        .isInstanceOf(ShortUrlNotFoundException.class);
  }
}
