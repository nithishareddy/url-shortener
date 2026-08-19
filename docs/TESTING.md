# Testing Approach, Limitations, Trade-offs

## Current approach: fully mocked, no database anywhere

Every test in this suite is a pure unit test. The repository/persistence layer is mocked with
Mockito, and controllers are tested with **standalone MockMvc** (`MockMvcBuilders.standaloneSetup`)
against a manually-constructed controller and mocked service — not `@SpringBootTest` or
`@WebMvcTest`. Concretely, that means:

- No `ApplicationContext` is bootstrapped by any test.
- No `DataSource`/H2/Postgres connection is ever opened.
- No Flyway migration ever runs during a test.
- Nothing under `src/test/` reaches the database, in-memory or otherwise.

This was a deliberate change from an earlier version of this suite that used `@SpringBootTest` +
an in-memory H2 database for integration-style tests (`*IT` classes under `integration/`). See
"What this trade-off actually costs" below — that's not a hidden regression, it's a decision made
explicitly and knowingly.

## Test inventory (51 tests)

| Class | Tests | What it mocks / how it avoids the DB |
|---|---|---|
| `ShortCodeGeneratorTest` | 4 | Pure logic, no dependencies at all |
| `UrlSafetyValidatorTest` | 6 | Pure logic (does real DNS resolution for the SSRF check, but touches no database) |
| `ShortUrlServiceTest` | 13 | Mocks `ShortUrlRepository` and `UrlSafetyValidator`; simulates the `IDENTITY`-generated id via `ReflectionTestUtils` since there's no real insert to produce one |
| `AnalyticsServiceTest` | 2 | Mocks `ShortUrlRepository` and `ClickEventRepository`; feeds synthetic `Object[]` rows shaped like what the native queries would return |
| `ClickTrackingServiceTest` | 2 | Mocks `ClickEventRepository`; verifies failures are swallowed, not propagated |
| `ShortUrlControllerTest` | 9 | Mocks `ShortUrlService`; standalone MockMvc with `GlobalExceptionHandler` wired in manually |
| `RedirectControllerTest` | 4 | Mocks `ShortUrlService` and `ClickTrackingService` |
| `AnalyticsControllerTest` | 2 | Mocks `AnalyticsService` |
| `HomeControllerTest` | 1 | No mocks needed — reads the real static file from the classpath, not a DB |
| `GlobalExceptionHandlerTest` | 4 | Calls handler methods directly; covers branches standalone MockMvc can't naturally trigger (`NoResourceFoundException`, the generic catch-all) |
| `RateLimitFilterTest` | 4 | No Spring/MockMvc at all — mocked `HttpServletRequest`/`Response`/`FilterChain` calling the filter directly |

## Division of labor: automated unit tests + manual verification

Mocking at the repository boundary proves the *service and controller logic* is correct in
isolation, fast and with zero external dependencies. The database- and stack-level properties are
covered by the manual pass instead, run against the real application (`mvn spring-boot:run`, real
H2 file-mode DB, real Flyway migrations):

- **The native SQL in `ClickEventRepository`** — `CAST(clicked_at AS DATE)`, `COALESCE`,
  `GROUP BY`, `LIMIT` — is exercised end-to-end by running analytics against real data, confirming
  the query is valid and dialect-compatible rather than by feeding `AnalyticsServiceTest`
  pre-shaped `Object[]` rows.
- **The Flyway migrations (`V1`–`V3`)** produce the schema in practice, since the app boots against
  them and the JPA entities map onto real columns every manual run.
- **The alias-uniqueness DB constraint** is confirmed live: a real duplicate-alias `POST` against
  the real schema returns `409`, in addition to `ShortUrlServiceTest` proving the service *reacts
  correctly* when a mock throws `DataIntegrityViolationException`.
- **`@Cacheable`/`@CacheEvict`/`@Async` as a live Spring AOP proxy** — the 30s-TTL cache/expiry
  interaction described in `docs/ARCHITECTURE.md` was reasoned through carefully and reviewed
  before it shipped (see `docs/SCENARIOS.md`), then confirmed by exercising it against the running
  app, since the unit tests call the annotated methods directly on a plain Java object and so
  don't go through the proxy.
- **The full HTTP stack wiring** — filters, `DispatcherServlet` routing, static-resource fallback —
  is where the root-path routing bug (`docs/SCENARIOS.md`, Scenario 3, Bug 3) was actually found,
  by running the real application; standalone MockMvc runs a controller in isolation, without the
  static-resource handler or `DispatcherServlet` that class of bug depends on.

This split is an accepted, explicit trade-off, not an oversight: every automated test runs in well
under a second combined and pinpoints failures precisely to one class, while the manual pass
covers the properties above.

## Manual verification

Checked manually against the running application: create → redirect → analytics → SSRF rejection →
custom alias → duplicate-alias 409 → deactivate → 410 → unknown code → 404 → the malformed-path 404
fix → the root-path demo-page fix → Swagger UI. This is a one-time manual pass per change, not yet a
repeatable gate; if this project grows past a prototype, the natural next step is to promote a
small slice of it (even just one, covering the create→redirect round trip against a real database)
into automated integration tests that run on every `mvn verify`. Kept manual here as an explicit,
deliberate scope decision for this iteration.

## Running the tests

```
mvn verify
```

Runs all unit tests (Surefire), the Spotless format check, and generates a JaCoCo coverage report
at `target/site/jacoco/index.html`. There is no Failsafe/`*IT` phase any more — nothing to run one
against.
