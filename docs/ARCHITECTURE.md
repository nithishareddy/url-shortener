# Architecture

## Overview

A single Spring Boot 3 / Java 21 service exposing:

- **Core shortening** — create a short code for a long URL, with optional custom alias and expiry.
- **Redirect** — resolve a short code to its long URL and 302-redirect.
- **Analytics** — click totals, daily breakdown, and top referrers per short URL.
- **Reliability features** — SSRF-safe URL validation, per-IP rate limiting on creation, a bounded
  cache on the redirect hot path, expiry, and soft-delete.

It's a single Maven module / single deployable — a URL shortener at this scope doesn't have
independent scaling or ownership boundaries between "create" and "redirect," so splitting it into
services would add operational cost (two deployables, network hop, distributed tracing) without a
concrete driver. If click-analytics volume ever needed independent scaling, that read path is
already isolated behind `AnalyticsService`/`ClickEventRepository` and could be extracted later.

## Components

```
                        ┌─────────────────────┐
   POST /api/urls  ───▶ │  ShortUrlController  │──▶ ShortUrlService ──▶ UrlSafetyValidator (SSRF guard)
                        └─────────────────────┘         │                ShortCodeGenerator (base62)
                                                          ▼
   GET  /{code}     ───▶  RedirectController ──▶ ShortUrlService.resolveForRedirect (cached, TTL 30s)
                                │                        │
                                ▼                        ▼
                         ClickTrackingService      short_url (H2/Postgres via JPA + Flyway)
                         (@Async, off hot path)          │
                                │                        ▼
                                ▼                  click_event
   GET /api/urls/{code}/analytics ──▶ AnalyticsController ──▶ AnalyticsService ──▶ ClickEventRepository
                                                                                   (native aggregation queries)

   POST /api/urls  ───▶ RateLimitFilter (Bucket4j, per-IP token bucket) — sits in front of ShortUrlController
```

Package layout (`src/main/java/com/schwab/urlshortener/`):

| Package | Responsibility |
|---|---|
| `web` | Controllers + `GlobalExceptionHandler` (RFC7807 `ProblemDetail` responses) |
| `service` | Business logic: `ShortUrlService`, `AnalyticsService`, `ClickTrackingService`, `ShortCodeGenerator`, `UrlSafetyValidator` |
| `domain` | JPA entities: `ShortUrl`, `ClickEvent` |
| `repository` | Spring Data repositories, including native aggregation queries for analytics |
| `dto` | Request/response records |
| `exception` | Domain exceptions, mapped to HTTP status in `GlobalExceptionHandler` |
| `config` | `AppProperties` (typed config), `CacheConfig`, `AsyncConfig`, `OpenApiConfig` |
| `ratelimit` | `RateLimitFilter` (Bucket4j, `OncePerRequestFilter`) |

## Control flow: create → redirect → analytics

1. **Create** (`POST /api/urls`): `RateLimitFilter` checks the per-IP bucket → `ShortUrlService.create`
   validates the URL (`UrlSafetyValidator`, SSRF-safe) → either claims a custom alias (with a
   reserved-word check and a DB-unique-constraint fallback for the race case) or inserts a row to
   get an `IDENTITY`-generated id, then base62-encodes that id into the short code
   (`ShortCodeGenerator`) and updates the row within the same transaction.
2. **Redirect** (`GET /{code}`): `ShortUrlService.resolveForRedirect` is `@Cacheable` (Caffeine,
   30s TTL) — a cache hit skips the DB entirely. On a hit or miss, expiry/active status is checked
   before returning; if gone, `410` is returned. On success, `ClickTrackingService.recordClick`
   is invoked `@Async` (a separate thread pool) so the click write never adds latency to the
   redirect response, then a `302` is returned.
3. **Analytics** (`GET /api/urls/{code}/analytics`): `AnalyticsService` resolves the short URL,
   then runs two native aggregation queries (daily counts, top referrers) plus a count query,
   assembled into one response.

## Key decisions

| Decision | Rationale | Trade-off accepted |
|---|---|---|
| Base62(sequential id) short codes | Deterministic, O(1), no collision-retry loop | Codes are guessable/enumerable in order; acceptable since links aren't access-control boundaries here |
| H2 file-mode by default, Postgres via profile | Reviewer can run the app with zero external setup | Two dialects to keep compatible (mitigated: only ANSI-standard SQL used in migrations/queries) |
| Click events as a raw append-only log, not pre-aggregated counters | Flexible ad-hoc aggregation (daily/referrer breakdowns) without a rollup job | Unbounded storage growth; retention/rollup is a documented follow-up, not built |
| Async click recording | Analytics writes never add latency to the redirect's critical path | At-most-once delivery — a click can be lost on crash between response and write |
| Caffeine cache with a 30s TTL on redirect lookups | Bounds DB load on the hot path | A link can remain reachable up to 30s after `expiresAt`/deactivation passes (deactivate explicitly evicts; expiry-by-time cannot) |
| SSRF/open-redirect guard resolves and checks the target host | Closes a real vector (metadata endpoints, internal services) | DNS-rebinding after validation is out of scope (see docs/SCENARIOS.md limitations) |
| In-memory rate limiting (Bucket4j) | Simplest correct option for a single instance | Does not hold cluster-wide across multiple instances — would need a shared store (e.g. Redis-backed Bucket4j) to scale out |

## Why three build phases, not one

The codebase was built in three passes that map directly to the three required scenarios (see
`docs/SCENARIOS.md` for the full decomposition/execution/validation for each):

1. **Greenfield**: minimal create + redirect, generated codes only, basic URL-format validation.
2. **Ambiguous → Analytics**: the "analytics" requirement had no spec; it's normalized
   and built as click-event tracking + aggregation.
3. **Brownfield hardening**: custom alias, expiry/soft-delete, SSRF validation, rate limiting, and
   caching added as real enhancements to the already-working service (schema evolves via `V3__...sql`
   rather than being designed into `V1` from the start).

This wasn't just cosmetic sequencing — building the naive version first is what surfaced the actual
open-redirect gap (phase 1's URL check only verified the scheme, not the target) and the cache/TTL
interaction bug (see `docs/ENGINEERING_LOG.md`) that phase 3 then fixed.
