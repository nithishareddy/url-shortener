# url-shortener

A URL shortener service (Spring Boot 3, Java 21) with core shorten/redirect APIs, click analytics,
and reliability features (SSRF-safe validation, rate limiting, caching, expiry, soft-delete).

Built as an AI-assisted engineering exercise across three phases — see [`docs/SCENARIOS.md`](docs/SCENARIOS.md)
for the full decomposition/execution/validation of each, [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
for the system design, [`docs/ENGINEERING_LOG.md`](docs/ENGINEERING_LOG.md) for AI-assistance
traceability, [`docs/TESTING.md`](docs/TESTING.md) for the test approach, and
[`docs/SUMMARY.md`](docs/SUMMARY.md) for the consolidated risks/trade-offs/assumptions.

## Requirements

- Java 21
- Maven (or use the repo as-is with your IDE's bundled Maven)
- Docker, only if you want the optional Postgres profile instead of the zero-setup default (H2)

## Run it

```bash
mvn spring-boot:run
```

Starts on `http://localhost:8080` with an embedded H2 database (file-backed at `./data/`, so data
survives restarts) — no external services required.

- Demo page: `http://localhost:8080/`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- Health check: `http://localhost:8080/actuator/health`

### Optional: run against Postgres

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Run the tests / quality gate

```bash
mvn verify
```

Runs unit tests (Surefire), integration tests (Failsafe, real H2 + Flyway), a Spotless format
check, and generates a JaCoCo coverage report at `target/site/jacoco/index.html`. See
[`docs/TESTING.md`](docs/TESTING.md) for what's covered.

## API quick tour

```bash
# Create a short URL
curl -X POST http://localhost:8080/api/urls \
  -H 'Content-Type: application/json' \
  -d '{"longUrl":"https://example.com/some/long/path"}'
# => { "shortCode": "1000001", "shortUrl": "http://localhost:8080/1000001", ... }

# Follow it
curl -i http://localhost:8080/1000001

# Check analytics
curl http://localhost:8080/api/urls/1000001/analytics

# Create with a custom alias and an expiry
curl -X POST http://localhost:8080/api/urls \
  -H 'Content-Type: application/json' \
  -d '{"longUrl":"https://example.com/promo","customAlias":"summer-sale","expiresAt":"2026-09-01T00:00:00Z"}'

# Deactivate a link
curl -X DELETE http://localhost:8080/api/urls/summer-sale
```

Full request/response schemas are in Swagger UI once the app is running.

## Project layout

```
src/main/java/com/schwab/urlshortener/
  web/         controllers + global exception handling
  service/     business logic
  domain/      JPA entities
  repository/  Spring Data repositories
  dto/         request/response records
  exception/   domain exceptions
  config/      typed config, cache, async, OpenAPI
  ratelimit/   Bucket4j rate-limit filter
src/main/resources/db/migration/   Flyway migrations
docs/                              architecture, scenarios, engineering log, testing, summary
```
