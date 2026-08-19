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

## What this trade-off actually costs

Mocking at the repository boundary proves the *service and controller logic* is correct in
isolation. It does **not** prove:

- **The native SQL in `ClickEventRepository` is actually valid** — `CAST(clicked_at AS DATE)`,
  `COALESCE`, `GROUP BY`, `LIMIT`. `AnalyticsServiceTest` feeds the service pre-shaped `Object[]`
  rows; it never executes that query against a real (or in-memory) database, so a typo or an
  H2/Postgres dialect incompatibility in that SQL would not be caught by any test in this suite.
- **The Flyway migrations (`V1`–`V3`) actually produce a schema the JPA entities can map onto** —
  column names, types, and constraints are asserted nowhere.
- **The alias-uniqueness DB constraint actually exists and works** — `ShortUrlServiceTest` proves
  the service *reacts correctly* to a `DataIntegrityViolationException`, by throwing one from a
  mock. It does not prove a real duplicate insert against the real schema actually throws one.
- **`@Cacheable`/`@CacheEvict`/`@Async` actually behave correctly as a live Spring AOP proxy** —
  these tests call the annotated methods directly on a plain Java object, so the caching and async
  dispatch behavior described in `docs/ARCHITECTURE.md` (the 30s-TTL cache/expiry interaction, for
  example) is asserted by nothing here. That fix is still correct — reasoned through and reviewed
  before it shipped (see `docs/SCENARIOS.md`) — but no automated test currently proves it holds.
- **The full HTTP stack wiring** — filters, `DispatcherServlet` routing, static-resource fallback.
  The root-path routing bug (`docs/SCENARIOS.md`, Scenario 3, Bug 3) was found by running the real
  application, not by a test, and standalone MockMvc — unlike a full Spring context — can't
  reproduce that class of bug at all (there's no static-resource handler or `DispatcherServlet`
  registered to collide with a controller's mapping).

This is an accepted, explicit trade-off, not an oversight: every test runs in well under a second
combined, has no external dependency, and pinpoints failures precisely to one class. The cost is
that the properties above are now verified only by manual testing (see below) and by having been
reasoned through carefully once, not by anything that runs on every `mvn verify`.

## Manual verification (fills the gap above)

Because the automated suite no longer touches a real database, the things it can't verify were
checked manually against the running application (`mvn spring-boot:run`, real H2 file-mode DB, real
Flyway migrations): create → redirect → analytics → SSRF rejection → custom alias → duplicate-alias
409 → deactivate → 410 → unknown code → 404 → the malformed-path 404 fix → the root-path demo-page
fix → Swagger UI. This is a one-time manual pass per change, not a repeatable gate — if this
project grows past a prototype, re-introducing a smaller set of true integration tests (even just
one, covering the create→redirect round trip against a real database) would be the natural next
step to close this gap with something that runs automatically. Not done here because it was an
explicit, deliberate scope decision for this iteration.

## Running the tests

```
mvn verify
```

Runs all unit tests (Surefire), the Spotless format check, and generates a JaCoCo coverage report
at `target/site/jacoco/index.html`. There is no Failsafe/`*IT` phase any more — nothing to run one
against.
