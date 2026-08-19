# Final Engineering Summary

## Plan and rationale

Built a Spring Boot 3 / Java 21 URL shortener in three phases mapped directly to the three required
scenarios (see `docs/SCENARIOS.md` for full decomposition/execution/validation of each):

1. **Greenfield** — core create + redirect, generated codes only.
2. **Ambiguous** — the undefined "analytics" requirement, normalized into a concrete
   spec (click-event log, async recording, read-time aggregation) before being built.
3. **Brownfield** — reliability hardening (custom alias, expiry/soft-delete, SSRF validation, rate
   limiting, caching) layered onto the already-working service via an additive schema migration and
   real incremental code changes, not folded into the original design.

The three-phase structure wasn't cosmetic — it's what surfaced two of the three real bugs caught
during the build (see below): the SSRF gap only became visible because phase 1 shipped without it
first, and the cache/expiry interaction was reasoned through specifically because caching was being
*added* to an already-working redirect path, not designed in from scratch.

## Artifacts delivered

- Working prototype: `mvn spring-boot:run` (H2, zero external setup) or `docker compose up -d` +
  `mvn spring-boot:run -Dspring-boot.run.profiles=postgres` for a production-like datastore.
- API surface: `POST /api/urls`, `GET /{shortCode}`, `GET /api/urls/{shortCode}`,
  `GET /api/urls/{shortCode}/analytics`, `DELETE /api/urls/{shortCode}`, `GET /actuator/health`.
- OpenAPI schema + Swagger UI (`springdoc`) at `/swagger-ui/index.html`.
- Minimal static demo page at `/` for a browser click-through without curl/Postman.
- 51 automated unit tests (Mockito-mocked, no database — see `docs/TESTING.md` for the trade-off),
  all green under `mvn verify` alongside a format check (Spotless) and coverage report (JaCoCo).
- `docs/ARCHITECTURE.md`, `docs/SCENARIOS.md`, `docs/ENGINEERING_LOG.md`, `docs/TESTING.md`, this
  file, and the root `README.md` (setup instructions).

## Risks and trade-offs (consolidated from ARCHITECTURE.md / SCENARIOS.md)

| Risk / trade-off | Mitigation in place | Residual risk |
|---|---|---|
| Short codes are sequential-derived (base62 of an id) → guessable/enumerable | None — accepted | Fine for a public link-shortening use case where codes aren't an access-control boundary; would need random codes if that assumption ever changes |
| Cache can serve an expired/deactivated link for up to 30s | Bounded TTL + explicit eviction on deactivate | A time-expired (not deactivated) link can be reachable briefly past its `expiresAt` |
| Click analytics are at-most-once (async, best-effort) | Failures are logged, never surfaced to the redirect response | Undercounting under crash/failure; acceptable for analytics, would not be acceptable for billing-grade metering |
| Rate limiting and caching are single-instance (in-memory) | Documented, not built out | Won't hold cluster-wide under horizontal scale-out without a shared store (Redis-backed Bucket4j / distributed cache) |
| SSRF guard doesn't re-validate existing rows or defend DNS-rebinding after validation | Guard applied at creation time for all new links | Pre-existing (pre-hardening) rows and TOCTOU DNS-rebinding are out of scope for this prototype's threat model |
| No auth/multi-tenancy | Out of scope by design; not silently assumed | Anyone can create/deactivate any link; would need auth before this is internet-facing for real users |
| `click_event` has no retention policy | None — accepted for prototype scope | Unbounded storage growth over time in a real deployment |

## Assumptions

- No user accounts/auth — multi-tenancy wasn't called for, and adding it would have meaningfully
  expanded scope without a clear requirement driver.
- Short codes and custom aliases share one namespace (a generated code can't collide with a
  custom alias) — simpler than partitioning them, and there was no reason to keep them
  separate.
- "Analytics" means engagement metrics on redirects, not business/revenue analytics — the
  reasonable reading for a link shortener with no purchase/conversion concept.
- A single deployable is appropriate at this scope (see `docs/ARCHITECTURE.md`, "Why one module").

## Limitations

- Not load-tested; correctness under concurrency is argued from the DB constraint (alias race) and
  transaction boundaries (short-code generation), not proven under generated load.
- No automated test executes against a real database at all (a deliberate, later decision — the
  suite was fully rewritten to Mockito-mocked unit tests; see `docs/TESTING.md` and
  `docs/ENGINEERING_LOG.md`, "Mid-project test-strategy pivot"). The native SQL, the Flyway schema,
  Postgres compatibility, and the `@Cacheable`/`@Async` proxy behavior are verified by manual
  testing against the running app, not by anything that runs on every `mvn verify`.
- No CI pipeline configured — `mvn verify` is the quality gate, run locally; OWASP dependency-check
  is documented as a recommended CI-only addition rather than wired into the local build (would
  require network access, undermining the "runs offline, zero setup" goal for a reviewer).
- No structured/centralized logging or distributed tracing — acceptable for a single-instance
  prototype, would be a gap in a real multi-instance deployment.

## Engineering judgment calls worth calling out explicitly

- Restructured the initial (AI-generated) draft of the service layer, which had bundled all
  hardening features into the greenfield build, into three genuine incremental phases — a process
  decision, not a code-generation one, made because a fabricated "brownfield" narrative over code
  that was never actually greenfield-then-enhanced would have undermined the whole point of that
  required scenario.
- Caught and fixed the cache/TTL-vs-expiry interaction bug during design review, before writing the
  unsafe version at all, rather than shipping it and fixing it later.
- Found and fixed a real routing bug (root path swallowed by the redirect catch-all) via manual
  end-to-end testing that the automated test suite did not catch — and added a regression test for
  it afterward, rather than treating the fix as sufficient on its own.
