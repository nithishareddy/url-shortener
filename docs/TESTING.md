# Testing Approach, Limitations, Trade-offs

## Approach

Two layers, run by two Maven plugins so they're clearly distinguished and both run on `mvn verify`:

| Layer | Naming | Plugin | What it covers |
|---|---|---|---|
| Unit | `*Test` | Surefire | Pure logic with no Spring context: `ShortCodeGenerator`, `UrlSafetyValidator` |
| Integration | `*IT` | Failsafe | Full Spring context + `MockMvc`, real H2 database, real Flyway migrations |

Integration tests use an in-memory H2 database (`spring.profiles.active=test`) so they run without
any external service, and go through the actual HTTP layer (`MockMvc`) rather than calling services
directly — this exercises validation, exception mapping, and status codes exactly as a real client
would see them, not just the service-layer logic.

## Current coverage (24 tests)

| Class | Tests | Focus |
|---|---|---|
| `ShortCodeGeneratorTest` | 4 | Determinism, uniqueness at scale, min length, character set |
| `UrlSafetyValidatorTest` | 6 | SSRF guard: valid URLs pass; malformed, non-http(s), loopback, RFC1918, and link-local metadata targets are rejected |
| `ShortUrlFlowIT` | 5 | Create→redirect happy path, blank/non-http(s) URL rejected, unknown code 404, root path regression |
| `AnalyticsFlowIT` | 2 | Async-recorded clicks reflected in analytics, unknown code 404 |
| `HardeningFlowIT` | 6 | Custom alias honored/conflict/reserved, SSRF rejection at the API layer, expired/deactivated → 410 |
| `RateLimitFlowIT` | 1 | Exceeding the create rate limit → 429, in its own tightly-configured context |

## Why async click-tracking tests aren't flaky

`ClickTrackingService.recordClick` is `@Async` in production so it never adds latency to a redirect
response. Testing this without either sleeping (slow, still technically racy) or polling
(`Awaitility`, adds a dependency) is done by swapping the named executor bean
(`clickTrackingExecutor`) for a `SyncTaskExecutor` in a `@TestConfiguration` scoped to
`AnalyticsFlowIT` only — the click write completes synchronously within the test's HTTP call, so
the analytics assertions that follow are deterministic.

## Why the rate-limit test has its own Spring context

`RateLimitFilter` holds its token buckets as singleton in-memory state for the lifetime of the
Spring context. Sharing one low-capacity config across every `*IT` class would make unrelated tests
fail depending on execution order and how many `POST /api/urls` calls preceded them. Instead:
the shared `test` profile uses a high capacity (100/min) so ordinary tests never hit the limit, and
`RateLimitFlowIT` overrides it down to 2/min via `@TestPropertySource`, which gives it a distinct
Spring context (and therefore a fresh, isolated `RateLimitFilter` instance) from every other test
class.

## Manual / exploratory testing

Automated tests don't replace running the actual application. After each phase, the app was started
with `mvn spring-boot:run` and exercised end-to-end with curl: create, redirect, analytics, SSRF
rejection, custom alias, duplicate alias, deactivate, expired link, unknown code, the static demo
page, and Swagger UI. This is what caught the root-path routing bug documented in
`docs/SCENARIOS.md` (Scenario 3, Bug 3) — no automated test exercised bare `GET /` before that
manual pass, and it's now a regression test.

## What's not covered, and why

| Gap | Why it's not covered | Risk if unaddressed |
|---|---|---|
| Load/concurrency testing (e.g. concurrent custom-alias claims under real parallelism) | The race is handled by a DB unique constraint + catch, but no test drives actual concurrent requests to prove it under load | Low — the constraint is the real correctness guarantee regardless of test coverage; a load test would add confidence, not correctness |
| Postgres-profile integration tests | Tests run against H2 only; Postgres is reachable via `docker-compose` but CI/test suite doesn't spin it up | Medium — a dialect-specific SQL issue in Postgres wouldn't be caught until manual verification; mitigated by using only ANSI-standard SQL in migrations and native queries |
| Cache TTL behavior (that a link actually becomes unreachable after the 30s window closes) | Would require a real-time sleep in the test suite, which is slow and flaky by nature | Low — the TTL value itself is straightforward config; the eviction *logic* is exercised by `deactivatedLinkReturnsGone` (explicit eviction path) |
| Rate limiter behavior across multiple app instances | Single-instance in-memory design, documented as a known scale-out limitation, not built | See `docs/ARCHITECTURE.md` |
| OWASP dependency-check / SCA scanning | Not wired into the local build — would need network access during `mvn verify`, which would make the "runs offline, zero setup" story for a reviewer less reliable | Recommended as a CI-only gate, not a local one — noted here rather than silently skipped |
| Coverage threshold enforcement | JaCoCo report is generated but not gated on a minimum percentage | For a 2–3 day prototype, a hard threshold risks encouraging low-value tests written to hit a number; the actual test list above was chosen to cover behavior, not a percentage |

## Running the tests

```
mvn verify
```

Runs unit tests, integration tests, the Spotless format check, and generates the JaCoCo report
(`target/site/jacoco/index.html`) — this is the single command that gates all three phases.
