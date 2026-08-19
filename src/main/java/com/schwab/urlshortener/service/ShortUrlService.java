package com.schwab.urlshortener.service;

import com.schwab.urlshortener.domain.ShortUrl;
import com.schwab.urlshortener.dto.CreateShortUrlRequest;
import com.schwab.urlshortener.exception.InvalidUrlException;
import com.schwab.urlshortener.exception.ShortUrlNotFoundException;
import com.schwab.urlshortener.repository.ShortUrlRepository;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShortUrlService {

  private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

  private final ShortUrlRepository repository;
  private final ShortCodeGenerator codeGenerator;

  public ShortUrlService(ShortUrlRepository repository, ShortCodeGenerator codeGenerator) {
    this.repository = repository;
    this.codeGenerator = codeGenerator;
  }

  @Transactional
  public ShortUrl create(CreateShortUrlRequest request) {
    validateUrlFormat(request.longUrl());

    ShortUrl shortUrl = new ShortUrl(request.longUrl());
    // IDENTITY generation forces the INSERT here so we have an id to encode; the row lands
    // fully-formed once the code is set below and the transaction commits.
    repository.saveAndFlush(shortUrl);
    shortUrl.setShortCode(codeGenerator.encode(shortUrl.getId()));
    return shortUrl;
  }

  @Transactional(readOnly = true)
  public ShortUrl resolveForRedirect(String shortCode) {
    return repository.findByShortCode(shortCode).orElseThrow(() -> new ShortUrlNotFoundException(shortCode));
  }

  @Transactional(readOnly = true)
  public ShortUrl getMetadata(String shortCode) {
    return repository.findByShortCode(shortCode).orElseThrow(() -> new ShortUrlNotFoundException(shortCode));
  }

  private void validateUrlFormat(String rawUrl) {
    URI uri;
    try {
      uri = new URI(rawUrl);
    } catch (URISyntaxException e) {
      throw new InvalidUrlException("Malformed URL");
    }
    if (uri.getScheme() == null || !ALLOWED_SCHEMES.contains(uri.getScheme().toLowerCase())) {
      throw new InvalidUrlException("Only http and https URLs are allowed");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      throw new InvalidUrlException("URL must include a host");
    }
  }
}
