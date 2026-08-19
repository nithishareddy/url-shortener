package com.schwab.urlshortener.repository;

import com.schwab.urlshortener.domain.ShortUrl;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShortUrlRepository extends JpaRepository<ShortUrl, Long> {

  Optional<ShortUrl> findByShortCode(String shortCode);

  boolean existsByShortCode(String shortCode);
}
