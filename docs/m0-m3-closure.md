# M0–M3 closure evidence

This checklist describes technical acceptance separately from release and teacher approval.
The exported tracker remains the planning source; this document does not change its statuses.

## Verified snapshot — 2026-11-02

- Java 25 host: clean Maven verification, 491 tests, zero failures/errors, four optional PostgreSQL tests skipped; full line/branch coverage gate passed.
- Java 17 Docker Maven stage: clean verification, the same 491-test result and coverage gate passed. This reproduces CI's JDK version.
- Frontend: build passed; 332 tests and 100% statements/branches/functions/lines.
- Isolated Compose acceptance: production images built, fresh PostgreSQL migrations and real nginx/HTTP/SMTP flows passed; temporary resources removed.
- Quality-tool logic: three automated tests passed. No three-chapter educational-quality approval is claimed.

## Repeatable technical checks

```sh
mvn -B clean verify
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/compose_acceptance.py
```

From the sibling frontend repository:

```sh
npm run build
npm run test:coverage
```

The Compose runner uses a random project name, dynamic localhost ports, a new PostgreSQL
database and a Mailpit instance. It builds the production Dockerfiles and checks migrations,
nginx's SPA fallback and API proxy, then runs synthetic HTTP/SMTP acceptance checks:

- Provision two independent centres and consume their emailed admin invitation links.
- Reject reused invitations, incorrect passwords and unauthenticated staff requests.
- Rotate a refresh token and continue using the issued access token.
- Invite a teacher through SMTP; enforce admin permissions and batch assignments.
- Add single/bulk roster entries, expose PINs only in creation responses, open student sessions,
  lock after five incorrect PINs, unlock/reset and reject deactivated students.
- Save centre settings and reject cross-centre roster access.
- Consume a password-reset email; reject token reuse, the old password and old refresh token.

Only synthetic `.invalid` recipients are used. The runner removes its own containers, network
and anonymous volumes in a `finally` block; it never cleans the development stack. It requires
Docker Compose and the sibling `learnium-frontend` checkout. SMTP delivery goes to Mailpit;
external email-provider delivery is a deployment acceptance check.

## Code gaps addressed

| Tracker item | Technical evidence / change | Remaining acceptance |
|---|---|---|
| #5 Compose | Isolated build and live HTTP/SMTP runner above; Docker contexts exclude host dependencies and generated files. | None after a passing run on the target checkout. |
| #6 CI | Java 17/25 build matrix, coverage gates, quality-tool tests and PostgreSQL migration job. Crypto failure tests use thread-scoped mocks instead of mutating global security providers. | Push and obtain green GitHub checks on the final commit. |
| #54 observability | `learnium.api.errors` counts MVC and security failures once, tagged only by registered `code` and HTTP `status`. Integration tests verify counts. Existing OTLP registry exports these counters with other metrics. | Configure the real collector and confirm the signals in Grafana. No credentials are stored in source. |
| #68 provenance | Queued jobs carry frozen provider/model into execution. Routing remains correct when the configured default changes; shared configuration is never mutated. Regression tests check both providers and the worker boundary. | New prompt/schema versions require explicit versioned implementations when their constants change. |
| #70 ER diagram | Root `ER-Diagram.md` contains all V1–V12 tables, columns and foreign keys, including composite tenancy constraints. Regenerate using `scripts/export_er_diagram.py`. | Regenerate after future migrations. |
| #72 M2 local validation | The live Compose runner above checks real nginx → backend → PostgreSQL/SMTP integration; unit/component suites cover token races, cache clearing, silent refresh and UI role guards. | Short user acceptance pass for layout, navigation, one-time PIN presentation and email link navigation in the intended browser/device. |
| #80 retry UI | `ZERO_VALID` is retryable. Both PENDING and RUNNING retries explain the automatic retry; different-mix advice appears only after terminal failure. Tests cover all three states. | None after frontend build and coverage pass. |

Invalid staff credentials return `LRN-AUTH-002`; `LRN-AUTH-001` means authentication is
required. The older #72 checklist's wrong-password expectation should be corrected.

## #20: three real chapters, at least 80% approvable

Generate questions from three distinct real chapters using the intended release provider,
model and question mix. Successful provider calls and schema validation do not establish
educational correctness. Ollama and Gemini need separate evidence if both are release options.

Set `LEARNIUM_ACCESS_TOKEN` securely in the shell (the collector never prints it), then:

```sh
python3 scripts/generation_quality.py collect \
  --jobs JOB_ID_1 JOB_ID_2 JOB_ID_3 --output chapter-review.json
```

The collector reads existing completed jobs and their drafts; it does not start AI calls or
modify data. It requires three distinct content hashes and the same provider/model. The report
records provenance, counts, payloads and correct references so a teacher can assess the actual
answers. It starts with `approved: null`; no question is approved automatically.

For each chapter, enter `reviewer`; for every question, set `approved` to `true` or `false`
and record rejection reasons in `reviewNotes`. Review factual accuracy, source grounding,
grade/difficulty suitability, clarity, duplicates, answer keys and written rubrics. Then run:

```sh
python3 scripts/generation_quality.py score chapter-review.json
```

The default gate is conservative: approved / requested questions must be at least 80% for
each chapter, so missing/dropped outputs count against the gate. Exit 0 means all three pass;
1 means reviewed but below target; 2 means incomplete or invalid evidence. This denominator
must be recorded with the approval rate rather than confused with backend structural yield.
Retain the teacher-reviewed report with release evidence. It contains teaching material and
should not be committed publicly without permission. Teacher review is still required.

## Scope correction for #44 and #58

M3 implements generation jobs and numeric draft payloads (`answerValue`, absolute `tolerance`,
`unit`, `workedSolution`). It does not yet implement the published student test/marking flow.
The existing tasks should be split before their full wording is marked complete:

- M3 #44: generation job scheduling, retry, leases, ownership and atomic draft persistence.
- M4/M5 grading jobs: grading job inputs/type, submission ownership and idempotency,
  failure/retry states, teacher confirmation, and tests proving that retries cannot mark twice.
- M3 #58: numeric question generation, validation and teacher preview.
- M5 numeric grading/player: tolerance-boundary and non-finite input tests, unit policy,
  attempt ownership, and worked-solution disclosure only after an authorized submission.

These are future student/marking features; placeholders are not evidence of completion.

## Runtime configuration

Gemini requires `GEMINI_API_KEY` from the environment; there is no inline key fallback.
Changing `GENERATION_PROVIDER` affects new jobs. Existing jobs retain their saved provider/model
and require that provider's credentials/endpoint to remain available while the queue drains.
Ollama settings include `OLLAMA_CONTEXT_WINDOW_TOKENS`, `OLLAMA_MAX_OUTPUT_TOKENS` and
`OLLAMA_THINK`; size the context for the actual chapters and output budget.
