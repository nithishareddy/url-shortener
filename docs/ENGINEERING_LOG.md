# Engineering Log — AI-Assisted Execution Traceability

This project was built with Claude Code as the AI pair — generating first drafts of code, config,
tests, and docs against tasks I (the engineer) defined with explicit intent, constraints, and
acceptance criteria. This log records what was generated, what I changed or rejected and why, and
what quality gates ran before each phase was considered done. It complements `docs/SCENARIOS.md`
(which covers the *what* and *why* of the three required scenarios) with the *how* of the AI-assisted
workflow itself.

## Working model

1. Define a task with an explicit acceptance criterion before generating code (e.g., "redirect
   must return 410, not 404, for an expired link" — not just "handle expiry").
2. Generate a first draft.
3. Review the draft against the acceptance criterion *and* against failure modes it doesn't
   mention (race conditions, concurrent-write conflicts, cache/consistency interactions).
4. Run the actual code — compile, unit test, integration test, and (at the end of each phase)
   the running app via curl — rather than accepting "looks right" as validation.
5. Course-correct in the log below when generation missed something.

## Human sign-off points

- **Plan approval before any code was written.** A full implementation plan (tech stack, API
  surface, package layout, phased scenario mapping) was presented and approved before the first
  file was created — this is the "engineer leads execution, AI assists within tasks" checkpoint for
  a change of this size.
- **Scope question mid-plan.** When the plan proposed link expiry as a feature, I asked "why do we
  have an expiry for shorturl?" before approving — the rationale (time-boxed campaign links,
  reclaiming stale codes, and giving the brownfield scenario a concrete reliability feature to build)
  was given and the feature was kept as an *optional* field, not forced onto every link. This is
  recorded because it's the kind of scope justification that should survive being asked twice.
- **No commits were made without being asked** — generation and local verification (compiling,
  testing) happened freely; anything that touches shared state (git history) is a separate,
  explicit approval step.

## Generated as-is vs. edited vs. rejected

| Item | Outcome | Rationale |
|---|---|---|
| Two-phase insert-then-update for short-code generation | Generated, kept as-is | Standard pattern for deriving a code from a DB-generated id; verified the transaction boundary makes it crash-safe |
| Bucket4j Maven coordinates (`bucket4j_jdk17-core`) | **Rejected** — build failure | Artifact doesn't exist on Maven Central under that name; verified against Maven Central search and corrected to `bucket4j_jdk11-core:8.14.0` |
| `Bandwidth.classic(...)` / `Refill.greedy(...)` for the rate limiter | **Rejected** — deprecated API | Compiled with deprecation warnings; switched to the current `Bandwidth.builder()...build()` API (verified via `javap` against the actual jar, since Bucket4j's docs/version drift is a known issue) |
| First draft of `ShortUrlService` (single pass, phase 1) | **Rejected the sequencing, not the code** | The first draft bundled custom-alias, expiry, and SSRF validation directly into the initial service — technically fine, but it would have made "brownfield hardening" a fabricated narrative with no real prior code to enhance. Rebuilt as three genuine incremental passes instead (see `docs/SCENARIOS.md`) |
| `@Cacheable` on `resolveForRedirect` with no TTL | **Rejected before implementing** | Reasoned through the failure mode (cache hit skips the expiry re-check entirely) before writing it; shipped with a bounded TTL + explicit eviction on deactivate instead. See Scenario 3, Bug 2 |
| Static demo page served from `classpath:/static` at `/` | **Edited after manual testing found a bug** | Initially assumed Spring Boot's default static-resource handling would "just work" at the root path; running the app and curling `/` proved otherwise (see Scenario 3, Bug 3). Added an explicit controller instead of trusting the framework default |
| RFC7807 `ProblemDetail` for all error responses | Generated, kept as-is | Consistent error shape reviewed against each new exception type as it was added (alias conflict, unsafe URL, gone, validation) — confirmed each maps to the correct HTTP status |
| `spring.main.allow-bean-definition-overriding=true` for one test's executor swap | Generated, kept, scoped narrowly | First attempt used `@Primary` alone and failed with `BeanDefinitionOverrideException`; the property is scoped to that one test class via `@TestPropertySource`, not set globally, so it doesn't mask real bean-definition conflicts elsewhere |

## Quality gates applied

Every phase was brought to green on the same gate before moving to the next:

```
mvn spotless:apply verify
```

- **Compile** — `javac` via Maven, target Java 21.
- **Format** — Spotless (Google Java Format), auto-applied then checked; `mvn verify` fails the
  build on any remaining violation.
- **Unit + integration tests** — Surefire (`*Test`) + Failsafe (`*IT`), 24 tests across all three
  phases, all green (see `docs/TESTING.md` for the breakdown).
- **Coverage** — JaCoCo report generated on `verify` (not gated on a hard threshold for this
  prototype; see `docs/TESTING.md` for what's covered vs. not).
- **Manual end-to-end smoke test** — the running app (`mvn spring-boot:run`) exercised directly with
  curl after each phase, not just the test suite. This is what caught Bug 3 in Scenario 3 — the
  test suite alone would not have (no test previously exercised bare `GET /`).

## What I did not delegate

- The decision to restructure the build into three genuine incremental phases rather than one
  monolithic pass (a scope/process call, not a code-generation task).
- The decision on what "analytics" should mean for this system (a product/requirements call).
- Verifying every generated dependency coordinate and API actually exists/compiles, rather than
  trusting it because it looked plausible (this caught the Bucket4j issues above).
- Running the actual application and clicking through it, rather than treating a green test suite
  as sufficient proof of correctness.
