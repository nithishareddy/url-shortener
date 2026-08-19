package com.schwab.urlshortener.service;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Rejects target URLs that would turn this service into an SSRF or open-redirect vector: only
 * http/https schemes are allowed, and the resolved host must not point at loopback, link-local, or
 * private (RFC1918) address space.
 *
 * <p>Added during the hardening pass, not the initial build: the greenfield version only checked
 * that the URL was syntactically a well-formed http(s) URI, which meant it would happily shorten
 * (and later redirect to) e.g. http://169.254.169.254/ — a cloud metadata endpoint. This validator
 * closes that gap for new links; it is not applied retroactively to links created before it shipped
 * (documented limitation, see docs/SCENARIOS.md).
 */
@Component
public class UrlSafetyValidator {

  private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

  public ValidationResult validate(String rawUrl) {
    URI uri;
    try {
      uri = new URI(rawUrl);
    } catch (URISyntaxException e) {
      return ValidationResult.invalid("Malformed URL");
    }

    if (uri.getScheme() == null || !ALLOWED_SCHEMES.contains(uri.getScheme().toLowerCase())) {
      return ValidationResult.invalid("Only http and https URLs are allowed");
    }
    if (uri.getHost() == null || uri.getHost().isBlank()) {
      return ValidationResult.invalid("URL must include a host");
    }

    try {
      InetAddress address = InetAddress.getByName(uri.getHost());
      if (isDisallowed(address)) {
        return ValidationResult.invalid(
            "URL host resolves to a private, loopback, or link-local address");
      }
    } catch (UnknownHostException e) {
      return ValidationResult.invalid("URL host could not be resolved");
    }

    return ValidationResult.ok();
  }

  private boolean isDisallowed(InetAddress address) {
    if (address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isAnyLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    if (address instanceof Inet4Address v4) {
      byte[] b = v4.getAddress();
      int first = b[0] & 0xFF;
      // 100.64.0.0/10 (carrier-grade NAT) — not covered by isSiteLocalAddress.
      if (first == 100 && (b[1] & 0xFF) >= 64 && (b[1] & 0xFF) <= 127) {
        return true;
      }
    }
    return address instanceof Inet6Address && address.isSiteLocalAddress();
  }

  public record ValidationResult(boolean valid, String reason) {
    static ValidationResult ok() {
      return new ValidationResult(true, null);
    }

    static ValidationResult invalid(String reason) {
      return new ValidationResult(false, reason);
    }
  }
}
