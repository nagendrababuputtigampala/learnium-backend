# Learnium Backend

Spring Boot 4 / Java 17 backend for the Learnium tutoring-centre platform:
teachers upload a chapter, AI drafts questions, the teacher approves them, students
take tests, and centres get per-batch weak-topic reporting with parent sharing.

This repository's `dev` branch is the fresh, production-grade rebuild. The previous
demo implementation is preserved untouched on `master`, tagged **`legacy-demo`**.

## Run it

### Option A — Docker Compose (whole platform)

Clone `learnium-frontend` next to this repo, then:

```bash
docker compose up --build
```

API on http://localhost:8080, frontend on http://localhost:5173, Mailpit (local
mail catcher) on http://localhost:8025, PostgreSQL on localhost:5432.

### Option B — Maven, against a local PostgreSQL

```bash
export DB_URL=jdbc:postgresql://localhost:5432/learnium
export DB_USERNAME=learnium
export DB_PASSWORD=learnium
mvn spring-boot:run
```

All configuration is environment-driven (see `application.yml` for the variables
and their local defaults). Secrets never live in the repo.

Health check: `GET /actuator/health` · App ping: `GET /api/v1/system/ping`

## Auth & accounts (M2)

Staff sign in with email + password (`POST /api/v1/auth/login` → 15-minute JWT
access token + rotating refresh token; replaying a revoked refresh token kills
the whole session family). Teachers are invited by the centre admin and set
their first password from the emailed link; password reset works the same way.
Students need no account: they pick their centre + batch from the public lists
and open a 12-hour session with their roster name + 4-digit PIN
(`POST /api/v1/auth/student-session`; 5 wrong PINs lock the roster entry until
a teacher/admin resets it). New centres are onboarded via
`POST /api/v1/platform/centres`, gated by a provisioning key.

Additional environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `JWT_SECRET` | *(blank)* | HS256 signing secret for access tokens. **Always set a real secret in production.** When blank (or set to the published development literal), startup fails unless a development gate is open: the `demo`/`dev` Spring profile or `JWT_DEV_SECRET_ALLOWED=true`. |
| `JWT_DEV_SECRET_ALLOWED` | `false` | Local development only: permits the published dev-only JWT fallback secret when no `JWT_SECRET` is set. Never enable in production. |
| `OUTBOX_ENCRYPTION_KEY` | *(blank → derived from the JWT secret)* | Key material for encrypting email-outbox payloads at rest (they carry raw set-password tokens until delivery). |
| `APP_PUBLIC_BASE_URL` | `http://localhost:5173` | Frontend origin used to build emailed set-password links. |
| `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025` | SMTP server for invite/reset mail (Mailpit locally). |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | *(blank)* | SMTP credentials, when the server requires them. |
| `MAIL_FROM` | `no-reply@learnium.local` | Sender address for transactional mail. |
| `MAIL_DISPATCH_DELAY_MS` | `5000` | How often the email outbox dispatcher polls. |
| `PLATFORM_PROVISIONING_KEY` | *(blank)* | Shared secret for the onboarding endpoint. Blank disables it (404). |

Demo data: run with the `demo` profile (`SPRING_PROFILES_ACTIVE=demo`) to seed
the Demo Tutoring Centre. The demo login now works end-to-end against the real
auth endpoints — **admin@demo.learnium.local / Demo@123** (teacher:
patel@demo.learnium.local, same password) — and the seeded Batch B roster
students sign in with PIN **1234**.

## Build & test

```bash
mvn -B verify
```

`verify` runs the full test suite and enforces the coverage gate: **100% line and
branch coverage on authored code** (JaCoCo). Only the application entry point and
machine-generated code (Lombok output) are excluded.

## Branch strategy

`feature/<name>` → `dev` (integration) → `master` (stable) → version tags
(`v0.1.0`, …). One feature = one branch = one reviewable merge request. `master`
is never committed to directly. Existing pre-rebuild branches and the
`legacy-demo` tag are kept as-is for reference/fallback.

## Conventions

### Error codes

Every API error returns the same body:

```json
{ "code": "LRN-VAL-001", "message": "Validation failed", "traceId": "…", "timestamp": "…" }
```

Codes follow `LRN-<DOMAIN>-<NNN>` and live in
`com.learnium.common.error.ErrorCode` — they are a public contract (clients,
support and alerting key off them) and never change meaning once shipped.
Domains so far: `SYS` platform, `VAL` validation, `AUTH` authentication /
authorization. Throw `BusinessException` for expected domain failures; anything
unexpected collapses to `LRN-SYS-001` with a generic message (detail goes to the
logs only, via `GlobalExceptionHandler`).

### Logging

Structured JSON logs (one object per line) via Logback + logstash-logback-encoder.
Every request gets a trace id: the caller's `X-Request-Id` header is reused when
present, otherwise one is generated (`TraceIdFilter`); it appears in MDC as
`traceId`/`requestId`, in every log line, in the `X-Request-Id` response header
and in error bodies. Log levels: ERROR = needs human attention (always with an
error code), WARN = recoverable/business rejection, INFO = lifecycle events.
Never log passwords, tokens, or raw student answer text — IDs only.

### Observability

Micrometer + OpenTelemetry (via `spring-boot-starter-opentelemetry`): traces and
metrics are produced in-process and exported over OTLP only when
`OTEL_EXPORTER_OTLP_ENDPOINT` is set (see `application.yml` —
`management.otlp.tracing.endpoint`, `management.otlp.metrics.export.url`,
`TRACING_SAMPLING`, `OTEL_METRICS_STEP`). Point it at Grafana Cloud today or at
Datadog/Splunk/Dynatrace later: the vendor is configuration, never code. Error
counters per LRN code will be incremented by the metrics-aware exception
handling in a later task; the tracing side already propagates the same
`traceId` the logs carry.

### Layering

`controller → service → repository`, DTOs at the API boundary, MapStruct for
mapping, Flyway for all schema changes (never Hibernate auto-DDL in production:
`ddl-auto: validate`).
