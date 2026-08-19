# Three Scenarios: Greenfield, Ambiguous, Brownfield

The assignment asks for three worked scenarios, each showing decomposition, execution, and
validation. Rather than construct three artificial examples, these are the three real phases the
codebase was actually built in — each commit-worthy on its own, each building on the last. Git
history (once committed, see the note at the end) reflects this directly.

---

## Scenario 1 — Greenfield: core shortening + redirect

### Requirement understanding

The assignment asks for "core APIs" for a URL shortener. Normalized: given a long URL, produce a
short code; given a short code, redirect to the long URL. Nothing else is load-bearing for a first
working slice — no analytics, no expiry, no auth, no custom naming.

### Decomposition

| # | Task | Depends on |
|---|---|---|
| 1 | Schema: `short_url(id, short_code, long_url, created_at)` | — |
| 2 | `ShortCodeGenerator`: base62(id), deterministic, no collisions | — |
| 3 | `ShortUrlService.create`: validate URL is a well-formed http(s) URI, persist, derive code | 1, 2 |
| 4 | `POST /api/urls` — create endpoint | 3 |
| 5 | `GET /{shortCode}` — redirect endpoint (302, or 404 if unknown) | 3 |
| 6 | Unit tests (encoder) + integration tests (create→redirect, validation, 404) | 4, 5 |

### Execution notes (AI-assisted, engineer-reviewed)

- The two-phase insert-then-update pattern for `short_code` (insert to get an `IDENTITY` id, then
  update with the base62-encoded code, all in one transaction) was proposed and used as-is — it's
  the standard way to derive a code from a DB-generated id without a second sequence or a
  collision-retry loop. I reviewed it for the failure mode (does a crash between insert and update
  leave a bad row?) — no, because both statements are in the same `@Transactional` method, so a
  crash rolls back the whole thing.
- Initial URL validation only checked scheme + non-blank host via `java.net.URI`. This was
  intentionally *not* hardened against SSRF at this stage — see Scenario 3, where testing this
  gap directly motivated `UrlSafetyValidator`.

### Validation

- `ShortCodeGeneratorTest`: determinism, uniqueness over 10k ids, minimum length, URL-safe
  character set.
- `ShortUrlServiceTest` / `ShortUrlControllerTest` / `RedirectControllerTest` (Mockito-mocked, no
  DB — see `docs/TESTING.md`): create→redirect happy path, blank URL rejected, non-http(s) scheme
  rejected, unknown code → 404.
- Manual: `mvn spring-boot:run`, curl create + redirect, confirmed `Location` header matches input.

### Risk / limitation carried forward

No host-level validation yet — a shortener that will redirect to *any* syntactically valid
http(s) URL, including internal/private targets, is an SSRF and open-redirect vector. Flagged
explicitly rather than silently shipped; addressed in Scenario 3.

---

## Scenario 2 — Ambiguous: "analytics"

### Requirement understanding (normalizing the ambiguity)

The assignment scope says "core APIs, analytics, and reliability features" — "analytics" is never
defined further. Questions that needed answers before writing code:

- **What's tracked?** Redirect events only (creation events aren't a meaningful engagement metric
  for a shortener).
- **What dimensions?** Timestamp, referrer, user-agent. Explicitly *not* IP address — storing raw
  client IPs is a PII/privacy concern disproportionate to the value for a prototype; geo-lookup
  from IP was considered and dropped as out-of-scope (needs a GeoIP database, no clear requirement
  driver).
- **Raw log or pre-aggregated counters?** Raw append-only `click_event` log. This trades storage
  growth (unbounded, no retention policy built) for flexibility — daily breakdowns and top-referrer
  queries can be added or changed later without redesigning the write path. A production version
  would need a retention/rollup job; documented as a follow-up, not built (YAGNI for a prototype).
- **Synchronous or real-time?** Read-time aggregation via SQL `GROUP BY`, no batch job — correct at
  prototype scale, simplest option that's still "real-time" (modulo async write lag, see below).
- **Does tracking affect the redirect's latency/availability?** No — this was the actual design
  driver. Click writes must never slow down or break a redirect.

### Decomposition

| # | Task | Depends on |
|---|---|---|
| 1 | Schema: `click_event(id, short_url_id, clicked_at, referrer, user_agent)` + FK + index | Scenario 1 schema |
| 2 | `ClickTrackingService.recordClick` — `@Async`, best-effort (log-and-swallow on failure) | 1 |
| 3 | Wire into `RedirectController`: record click after resolving, before responding | 2 |
| 4 | `AnalyticsService` — total clicks, last-30-days daily breakdown, top-5 referrers | 1 |
| 5 | `GET /api/urls/{code}/analytics` | 4 |
| 6 | Integration test proving async-recorded clicks show up in the analytics response | 2–5 |

### Execution notes (AI-assisted, engineer-reviewed)

- Async execution needed its own bounded thread pool (`AsyncConfig`) rather than the default
  `SimpleAsyncTaskExecutor` — an unbounded default executor under a click burst would spin up
  unbounded threads. Reviewed and changed to a `ThreadPoolTaskExecutor` with a bounded queue.
- The aggregation queries were written as native SQL (`CAST(clicked_at AS DATE)`, `COALESCE`,
  `GROUP BY`) rather than JPQL, specifically checked for portability across H2 and Postgres (both
  support this ANSI-standard syntax) since the app supports both as datasource profiles.
- Testing async code without flakiness: `ClickTrackingServiceTest` calls `recordClick` directly on
  a plain (non-Spring-proxied) instance, so it runs synchronously in the test — there's no `@Async`
  dispatch to race against. This sidesteps flakiness entirely but, per `docs/TESTING.md`, also means
  no automated test currently proves the real `@Async` proxy dispatches onto the dedicated executor
  as configured; that's verified by manual testing instead.

### Validation

- `AnalyticsServiceTest` (Mockito-mocked repositories, no DB): given synthetic rows shaped like the
  native queries' output, assembles the correct `totalClicks`/`clicksByDay`/`topReferrers`; unknown
  code → `ShortUrlNotFoundException`. `ClickTrackingServiceTest`: records a click with the given
  details; swallows a repository failure rather than propagating it.
- Manual: created a link, curled the redirect twice with different `Referer` headers, confirmed the
  analytics response reflected both — this is also where the native SQL and Flyway schema were
  actually exercised, since the automated suite no longer does (`docs/TESTING.md`).

### Risk / limitation carried forward

- At-most-once click delivery — a crash between the redirect response and the async DB write loses
  that click. Acceptable for a prototype; a production version would use an outbox pattern or a
  durable queue if click-accuracy became a hard requirement.
- No retention policy on `click_event` — unbounded growth over time, flagged as a follow-up.

---

## Scenario 3 — Brownfield: hardening the existing service

### Requirement understanding

The assignment scope explicitly calls for "reliability features," and the brownfield category
covers enhancements/refactors/bug fixes against existing code. By this point the service already
had two real gaps worth treating as brownfield work rather than upfront design: the SSRF surface
from Scenario 1, and (found during this pass, before it shipped) a cache/expiry interaction bug.

### Codebase reasoning (what's impacted)

- `short_url` table needs new columns (`custom_alias`, `expires_at`, `active`) — additive `ALTER
  TABLE` in `V3__...sql`, not a `V1` redesign, because the table already has production-shaped data
  flowing through it in this narrative.
- `ShortUrlService.create` needs a second path (custom alias) alongside the existing generated-code
  path — decomposed as `createWithCustomAlias` / `createWithGeneratedCode`, sharing the SSRF check.
- `ShortUrlService.resolveForRedirect` needs to reject inactive/expired links — and, once caching is
  added to that same method, needs the cache to not defeat that check.
- `ShortUrlController` needs a new endpoint (`DELETE`) for soft-delete.
- A new filter (`RateLimitFilter`) needs to sit in front of the existing `POST /api/urls` without
  touching its controller code.

### Decomposition

| # | Task | Depends on |
|---|---|---|
| 1 | Migration `V3`: add `custom_alias`, `expires_at`, `active` to `short_url` | Scenario 1 schema |
| 2 | `UrlSafetyValidator`: SSRF guard (scheme allowlist + resolved-address blocklist) | — |
| 3 | Custom alias support: reserved-word check, uniqueness check + DB-constraint fallback for the race | 1 |
| 4 | Expiry + soft-delete: `isRedirectable()`, `DELETE /api/urls/{code}` | 1 |
| 5 | `RateLimitFilter` (Bucket4j, per-IP, scoped to `POST /api/urls`) | — |
| 6 | `CacheConfig` (Caffeine, bounded TTL) applied to `resolveForRedirect` | 4 |
| 7 | Tests for every new failure mode: alias conflict, reserved alias, SSRF-rejected, expired-link 410, deactivated-link 410, rate-limit 429 | 1–6 |

### Execution notes (AI-assisted, engineer-reviewed) — two real bugs caught before shipping

**Bug 1 — SSRF gap (carried from Scenario 1, closed here).** The greenfield validator only checked
URL syntax. `UrlSafetyValidatorTest` explicitly exercises `http://169.254.169.254/...` (a cloud
metadata endpoint) and RFC1918 ranges to prove the closed gap. Documented limitation: this is not
applied retroactively to links created before the validator shipped.

**Bug 2 — cache/expiry interaction, caught during review before it shipped.** The first draft of
`resolveForRedirect` added `@Cacheable` with no TTL. Reasoning through it before implementing:
`isRedirectable()` is only evaluated on a cache *miss* — the method body doesn't re-run on a hit. An
unbounded cache would mean a link cached while still valid would keep being served as redirectable
*forever* from cache, even after its `expiresAt` passed or it was deactivated, because the
expiry/active check simply wouldn't execute again. Fixed by giving the cache a short TTL (30s) and
by explicitly evicting on `deactivate()` — deactivation is instant, time-based expiry is bounded to
a 30s staleness window, called out as an accepted trade-off in `docs/ARCHITECTURE.md`. This is
documented here as caught-during-review rather than shipped-then-fixed: the broken version was
reasoned through and never committed, but the failure mode is real and the fix (TTL + explicit
eviction) is directly a response to it.

**Bug 3 — found via manual smoke-testing, fixed immediately.** After wiring in a static demo page
at `/`, manual end-to-end testing (`curl http://localhost:8080/`) returned a 404 with the body `"No
short URL found for code 'index.html'"` — not what a homepage request should ever produce. Root
cause: `RedirectController`'s `/{shortCode}` pattern lives in Spring's `RequestMappingHandlerMapping`,
which is checked *before* the low-priority static-resource handler; Spring Boot's welcome-page
mechanism internally forwards `/` → `/index.html`, and that forwarded request matched
`/{shortCode}` (`shortCode="index.html"`) before the static resource handler ever got a chance.
Fixed with an explicit `@GetMapping("/")` in a new `HomeController` that serves the file directly
(not via forward, to avoid re-entering the same collision) — an exact-literal mapping ranks above a
path-variable mapping within the same handler mapping, so it wins. A regression test
(`RoutingTest`) locks this in — registering `HomeController` and `RedirectController` together
under one standalone `MockMvc` dispatcher, since testing either controller in isolation (as
`HomeControllerTest`/`RedirectControllerTest` do) can't reproduce a collision that only exists when
both share one routing table. This is the clearest example in the whole build of validation (not
code review) catching something review alone would likely have missed — it only surfaced by
actually running the app and hitting it with a browser/curl.

### Validation

Test suite was rewritten mid-project to be fully Mockito-mocked with no database anywhere (an
explicit engineering decision — see `docs/TESTING.md` for what that costs and why it was accepted
anyway):

- `ShortUrlServiceTest`: custom alias honored, duplicate alias (pre-check and the DB-constraint-race
  path) → `AliasConflictException`, reserved alias → `InvalidAliasException`, unsafe URL rejected,
  expired/deactivated link → `ShortUrlGoneException`.
- `ShortUrlControllerTest` / `RedirectControllerTest`: the same scenarios asserted at the HTTP layer
  (409/400/410) against a mocked service.
- `RateLimitFilterTest`: exceeding the configured limit → `429`, direct unit test on the filter
  (mocked servlet request/response/chain, no MockMvc or Spring context at all).
- `UrlSafetyValidatorTest`: unit-level coverage of the SSRF guard's decision logic in isolation.
- `RoutingTest`: the root-path regression above.
- Manual end-to-end (`mvn spring-boot:run` + curl, against the real H2-backed app): create →
  redirect → analytics → SSRF-rejected create → custom alias → duplicate-alias 409 → deactivate →
  410 → unknown code → 404 → demo page at `/` → Swagger UI at `/swagger-ui/index.html`. This is the
  layer that now exercises the real Flyway schema, native SQL, and cache/DB interaction — none of
  which the (now fully mocked) automated suite touches any more (`docs/TESTING.md`), and it's also
  where Bug 3 above was actually found.
- Full quality gate: `mvn verify` — unit tests, Spotless format check, JaCoCo coverage report, all
  green.

### Risk / limitation carried forward

- Rate limiting and caching are in-memory/single-instance; a multi-instance deployment needs a
  shared store for both to hold cluster-wide (see `docs/ARCHITECTURE.md`).
- SSRF validation is not re-applied to pre-existing rows, and does not defend against DNS rebinding
  after validation passes (TOCTOU on the resolved address) — acceptable for a prototype threat
  model, called out explicitly rather than silently assumed away.

---

## Note on git history

These three scenarios were implemented in this order in one working session; the plan (approved
before implementation, see the assistant's plan-mode transcript) commits to staging them as three
separate, reviewable commits — greenfield core, ambiguous/analytics, brownfield hardening — rather
than one monolithic commit, specifically so the "brownfield" commit is a real diff against
already-committed code, not a narrative applied after the fact.
