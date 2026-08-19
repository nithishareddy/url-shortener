# url-shortener

## What is this?

This is a **URL shortener service** — like bit.ly or tinyurl — built with Java (Spring Boot).
You give it a long link, it gives you back a short one. When someone visits the short link, they
get redirected to the original long link automatically.

Besides the basic shorten/redirect feature, it also includes:

- **Click analytics** — see how many times a short link was clicked, on which days, and where the
  clicks came from.
- **Custom aliases** — pick your own short link name instead of a random code (e.g.
  `yoursite.com/summer-sale`).
- **Link expiry** — optionally set a link to stop working after a certain date.
- **Safety and reliability features** — protection against malicious links, limits on how many
  links one person can create per minute, and caching so redirects are fast.

It was built as an AI-assisted engineering exercise in three stages. If you want the full story of
*how* and *why* it was built this way, see the [`docs/`](docs/) folder — but everything you need to
just run it is in this file.

## What you need before you start

- **Java 21** installed
- **Maven** installed (or just use the copy bundled with your IDE)
- **Docker** — only needed if you want to try the optional Postgres database setup; not required
  for normal use

To check what you have:

```bash
java -version
mvn -version
```

If `java -version` doesn't show version 21, see the "Wrong Java version" fix below.

## How to build it

Open a terminal in the project folder and run:

```bash
cd /Users/nithishareddy/java-workspace/url-shortener
mvn clean install
```

This downloads dependencies, compiles the code, and runs the tests. If it prints
`BUILD SUCCESS` at the end, you're good. If it fails, see the "Common build errors" section below.

## How to run it

```bash
mvn spring-boot:run
```

Wait until you see a line like `Started UrlShortenerApplication in ... seconds` — that means it's
running. It uses a built-in database (H2), so there's nothing else to install or start first.

Once it's running, open in your browser:

- **`http://localhost:8080/`** — a simple page to try shortening a link
- **`http://localhost:8080/swagger-ui/index.html`** — interactive API documentation
- **`http://localhost:8080/actuator/health`** — should show `{"status":"UP"}` if it's healthy

To stop it, go back to the terminal and press `Ctrl+C`.

## Trying the API from the command line

```bash
# Create a short URL
curl -X POST http://localhost:8080/api/urls \
  -H 'Content-Type: application/json' \
  -d '{"longUrl":"https://example.com/some/long/path"}'
# => { "shortCode": "1000001", "shortUrl": "http://localhost:8080/1000001", ... }

# Follow the short link (redirects to the long one)
curl -i http://localhost:8080/1000001

# Check click analytics
curl http://localhost:8080/api/urls/1000001/analytics

# Create a link with your own custom name and an expiry date
curl -X POST http://localhost:8080/api/urls \
  -H 'Content-Type: application/json' \
  -d '{"longUrl":"https://example.com/promo","customAlias":"summer-sale","expiresAt":"2026-09-01T00:00:00Z"}'

# Turn off a link
curl -X DELETE http://localhost:8080/api/urls/summer-sale
```

Full request/response formats are in Swagger UI once the app is running.

## Running the tests

```bash
mvn verify
```

This runs all automated tests, checks code formatting, and creates a coverage report at
`target/site/jacoco/index.html`. See [`docs/TESTING.md`](docs/TESTING.md) for details on what's
tested.

## Common build errors, and how to fix them

**"Could not resolve dependencies" / "missing artifact" errors**
Maven couldn't download something it needs, or it's using an old cached failure. Fix:
```bash
mvn clean install -U
```
The `-U` forces Maven to re-check for updates instead of trusting its cache. If that still fails,
delete the specific stuck dependency folder from `~/.m2/repository/` and try again (the error
message tells you the folder name — e.g. `com.bucket4j` becomes `~/.m2/repository/com/bucket4j`).

**Build works in the terminal but fails in your IDE**
Your IDE has its own cached copy of the project. Reload it:
- VS Code: Command Palette → "Java: Clean Java Language Server Workspace", or reload the window
- IntelliJ: right-click the project → Maven → "Reload Project"
- Eclipse: right-click the project → Maven → "Update Project"

**Wrong Java version**
Check `java -version` shows 21. If you have multiple Java versions installed, point Maven at the
right one for this terminal session:
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

**"Port 8080 is already in use"**
Something else is already running on that port. Either stop it, or find and stop the old process:
```bash
lsof -i :8080
kill -9 <PID shown above>
```

**A terminal command seems stuck / won't return control**
Press `Ctrl+C` to cancel it. If that doesn't work, open a new terminal and force-stop it:
```bash
pkill -9 -f "mvn"
```

**Tests fail with database/schema errors**
Delete the local database file and try again — it will be recreated automatically:
```bash
rm -rf data/
mvn clean verify
```

If none of these fix it, run the build with more detail and share the actual error text:
```bash
mvn clean verify -e
```

## Optional: running with Postgres instead of the built-in database

Not required for normal use, but if you want to test against a real database:

```bash
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Project structure

```
src/main/java/com/schwab/urlshortener/
  web/         controllers + error handling
  service/     business logic
  domain/      database entities
  repository/  database access
  dto/         request/response data shapes
  exception/   custom error types
  config/      app configuration (cache, async, API docs)
  ratelimit/   rate-limiting filter
src/main/resources/db/migration/   database migration scripts
docs/                              architecture, scenarios, engineering log, testing, summary
```

## Learn more

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — how the system is designed and why
- [`docs/SCENARIOS.md`](docs/SCENARIOS.md) — the three build phases in detail
- [`docs/ENGINEERING_LOG.md`](docs/ENGINEERING_LOG.md) — what was AI-generated vs. hand-reviewed/changed
- [`docs/TESTING.md`](docs/TESTING.md) — testing approach and known gaps
- [`docs/SUMMARY.md`](docs/SUMMARY.md) — risks, trade-offs, and assumptions
