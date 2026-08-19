package com.schwab.urlshortener.service;

import com.schwab.urlshortener.config.AppProperties;
import com.schwab.urlshortener.config.CacheConfig;
import com.schwab.urlshortener.domain.ShortUrl;
import com.schwab.urlshortener.dto.CreateShortUrlRequest;
import com.schwab.urlshortener.exception.AliasConflictException;
import com.schwab.urlshortener.exception.InvalidAliasException;
import com.schwab.urlshortener.exception.ShortUrlGoneException;
import com.schwab.urlshortener.exception.ShortUrlNotFoundException;
import com.schwab.urlshortener.exception.UnsafeUrlException;
import com.schwab.urlshortener.repository.ShortUrlRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortUrlService {

  private final ShortUrlRepository repository;
  private final ShortCodeGenerator codeGenerator;
  private final UrlSafetyValidator safetyValidator;
  private final AppProperties appProperties;

  public ShortUrlService(
      ShortUrlRepository repository,
      ShortCodeGenerator codeGenerator,
      UrlSafetyValidator safetyValidator,
      AppProperties appProperties) {
    this.repository = repository;
    this.codeGenerator = codeGenerator;
    this.safetyValidator = safetyValidator;
    this.appProperties = appProperties;
  }

  @Transactional
  public ShortUrl create(CreateShortUrlRequest request) {
    var validation = safetyValidator.validate(request.longUrl());
    if (!validation.valid()) {
      throw new UnsafeUrlException(validation.reason());
    }

    boolean useCustomAlias = request.customAlias() != null && !request.customAlias().isBlank();
    return useCustomAlias
        ? createWithCustomAlias(request, request.customAlias())
        : createWithGeneratedCode(request);
  }

  private ShortUrl createWithCustomAlias(CreateShortUrlRequest request, String alias) {
    if (appProperties.reservedAliases().contains(alias.toLowerCase())) {
      throw new InvalidAliasException("Alias '" + alias + "' is reserved");
    }
    if (repository.existsByShortCode(alias)) {
      throw new AliasConflictException(alias);
    }
    ShortUrl shortUrl = new ShortUrl(request.longUrl(), true, request.expiresAt());
    shortUrl.setShortCode(alias);
    try {
      return repository.saveAndFlush(shortUrl);
    } catch (DataIntegrityViolationException e) {
      // Race: two requests claimed the same alias concurrently; the unique constraint is the
      // source of truth, this catch just turns it into the same 409 as the pre-check above.
      throw new AliasConflictException(alias);
    }
  }

  private ShortUrl createWithGeneratedCode(CreateShortUrlRequest request) {
    ShortUrl shortUrl = new ShortUrl(request.longUrl(), false, request.expiresAt());
    // IDENTITY generation forces the INSERT here so we have an id to encode; the row lands
    // fully-formed once the code is set below and the transaction commits.
    repository.saveAndFlush(shortUrl);
    shortUrl.setShortCode(codeGenerator.encode(shortUrl.getId()));
    return shortUrl;
  }

  @Cacheable(cacheNames = CacheConfig.SHORT_URL_LOOKUP_CACHE, key = "#shortCode")
  @Transactional(readOnly = true)
  public ShortUrl resolveForRedirect(String shortCode) {
    ShortUrl shortUrl =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));
    if (!shortUrl.isRedirectable()) {
      throw new ShortUrlGoneException(shortCode);
    }
    return shortUrl;
  }

  @Transactional(readOnly = true)
  public ShortUrl getMetadata(String shortCode) {
    return repository
        .findByShortCode(shortCode)
        .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));
  }

  @CacheEvict(cacheNames = CacheConfig.SHORT_URL_LOOKUP_CACHE, key = "#shortCode")
  @Transactional
  public void deactivate(String shortCode) {
    ShortUrl shortUrl =
        repository
            .findByShortCode(shortCode)
            .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));
    shortUrl.deactivate();
  }
}
