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

### Layering

`controller → service → repository`, DTOs at the API boundary, MapStruct for
mapping, Flyway for all schema changes (never Hibernate auto-DDL in production:
`ddl-auto: validate`).
