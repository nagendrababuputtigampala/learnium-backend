# Progress — feature/student-flow (M5)

## Part 5 — integrity + camera (feature/integrity-camera, Edison-built, 2026-11-03)

- S1 done: Flyway V18 (integrity_events + proctor_snapshots, composite tenancy
  FKs, CHECK constraints on type/reason), IntegrityEvent + ProctorSnapshot
  entities and repositories. Design note: proctor_snapshots.attempt_id is
  NULLABLE with (exam_instance_id, user_id) NOT NULL — the PRE_TEST camera
  check happens before an attempt exists, so the row is created against
  instance+student, carries the hashed check token, and is linked to the
  attempt when the token is consumed at start. MigrationTest 9 green
  (V1–V18 in order, tenancy FK + CHECK rejection proven).
- S2 done: POST /student/attempts/{id}/integrity-events — own attempt only,
  batch ≤ 20 validated up front, recorded only when the instance has signals
  ON and the attempt is not invalidated (otherwise accepted + dropped);
  maintains exam_attempts.integrity_flag_count. IntegrityFlowTest 4 green.
- S3 done: teacher attempt oversight (AttemptAdminService) —
  GET /instances/{id}/attempts with live score state + grouped flag counts,
  GET /attempts/{id}/integrity-events timeline, POST /attempts/{id}/invalidate
  (idempotent; managing staff + admins only, 404 otherwise). Marking queue and
  detail now exclude INVALIDATED attempts; a student result for an invalidated
  attempt reads LRN-ATT-001. AttemptAdminFlowTest 3 green.
- S4 done: camera check — POST /student/tests/{id}/camera-check stores the
  PRE_TEST frame via StorageService and returns a 10-minute single-use token
  (SHA-256 hash only on the snapshot row); camera-ON starts require a live
  check (new LRN-ATT-004), resume does not, consumption links the snapshot to
  the attempt. Frames are magic-byte validated (JPEG/PNG/WebP) and capped at
  2 MB. CameraCheckFlowTest 3 green.
- S5 done: POST /student/attempts/{id}/snapshots (PERIODIC/EVENT, own
  in-progress attempt, camera-ON only); teacher GET snapshot list + image
  stream under /attempts/{id}/snapshots with an audit log line per view;
  ProctorRetentionJob nightly-purges snapshots older than 30 days (object +
  row). SnapshotFlowTest 2 green.
- S6 done (tracker 86): confirm() now requires PENDING/MARKING_PENDING
  (CONFIRMED keeps LRN-MRK-002, anything else LRN-MRK-001); the marking
  queue accepts optional page/size params (paging applied to pending answers
  before grouping; no params = whole queue). MarkingQueueFlowTest 11 green.
- S7 done (coverage fix round, commit 190b62d): the first from-scratch full
  run surfaced two test bugs (a pre-Part-5 unit mock missing the new
  cameraMode stub; the camera fixture's maxAttempts=1 making the exhausted
  check fire before the camera gate on the burned-token start — fixed by
  publishing the fixture with 2 attempts) and bundle branch coverage at 96%
  on the new classes. Added ProctorServiceTest + IntegrityServiceTest +
  ProctorRetentionJobTest unit tests and flow coverage for the remaining
  branches (image magic-byte near-misses, snapshot content types,
  wrong-attempt/unlinked snapshot 404s, staged marking states,
  AUTO_SUBMITTED, size>100 paging bound, MARKING_PENDING confirm). Removed
  two dead code paths the gate exposed (the unused unpaged
  MarkingQueueService.queue overload; a null-request ternary in
  StudentController that Spring's body handling makes unreachable).
- Final gate (2026-11-03, from scratch): **656 tests, 0 failures, 41
  PG-gated skipped, JaCoCo 100% lines + branches — BUILD SUCCESS.**
  (620 tests on merged dev before Part 5.)
- Next: frontend gates, then bundles for Nagendra's push. Failing tests: none.

## Part 4 — student dashboard (2026-11-03)

- Added a student-owned dashboard computed live from final attempt and marking facts;
  invalidated attempts and pending written answers cannot affect scored denominators.
- Topic summaries expose confirmed counts, earned/available marks, exact threshold bands,
  strong/focus lists and the five most recent submitted results with pending indicators.
- Batch/topic comparisons appear only from eight distinct students, expose aggregate
  average and percentile data only, and never return another student's identity or score.
- Repository aggregation uses three fixed queries for topic, recent-result and comparison
  facts, avoiding per-topic, per-attempt and per-student query growth.
- Clean verification: 620 tests run, 579 passed and 41 optional PostgreSQL tests skipped;
  zero failures/errors. JaCoCo is 100% lines (3629/3629) and branches (1391/1391).
- Part 4 is complete and committed before Part 5; no integrity/camera work has started.

## Part 3 — marking queue and confirmation (2026-11-03)

- Flyway V17 replaces the blanket one-job-per-answer constraint with one live marking
  job per answer while retaining terminal history, and records confirmed criterion
  breakdowns plus marker/time audit fields.
- Added manageable-batch/admin marking queues and frozen-answer detail, with READY,
  MARKING, FAILED and manual NONE states derived from persisted answer/job facts.
- Added frozen-rubric confirmation with exact criterion/bounds validation, half-up
  rescaling to published marks, double-confirm protection and an audited manual path.
  Failed unconfirmed suggestions can be rerun without permitting duplicate live jobs.
- Database-level tests prove terminal history and the one-live-job invariant; API tests
  cover scoping, every state, validation, rounding, manual confirmation and rerun rules.
- Clean verification: 617 tests run, 576 passed and 41 optional PostgreSQL tests skipped;
  zero failures/errors. JaCoCo remains 100% lines (3581/3581) and branches (1365/1365).
- Part 3 is complete and committed before Part 4; no dashboard work has started.

## Checkpoint B — student player and practice (2026-11-03)

- Flyway V16 completes the ported attempt model with server deadlines, the four-state
  lifecycle and a tenant-safe frozen-item answer link unique within each attempt.
- Added assignment-scoped start/resume, owned attempt retrieval, typed idempotent autosave,
  first-writer submission, objective grading, written marking-job enqueueing and retakes.
  Pre-release responses remove every answer key, model answer, rubric and worked solution.
- Added the scheduled auto-submit path over the same grading transaction, including early
  instance closure, and release-policy-aware results with pending/confirmed written states.
- Flashcard practice returns frozen assigned cards in-window without creating attempts,
  answers, scores or other server-side practice progress.
- Final clean verification: 606 tests run, 565 passed and 41 optional PostgreSQL tests
  skipped; zero failures/errors. The enforced JaCoCo gates are 100% lines (3461/3461)
  and branches (1287/1287).
- Checkpoint B is complete and committed before Part 3; no Part 3 work has started.

## Checkpoint A — student accounts and Tests home (2026-11-03)

- Flyway V15 replaces roster PIN sessions with centre-linked student accounts: centres
  receive deterministic unique codes, students receive centre-scoped usernames, shared
  optional contact emails remain legal, and the retired PIN columns are removed.
- Staff and students now use the same login, JWT, refresh and account-status lifecycle.
  Staff authenticate by email; students authenticate as `username@centrecode`. The old
  student-session endpoint and batch-membership credential token have been removed.
- Roster management creates real student accounts, profiles, memberships and optional
  invites transactionally. It supports suggested/custom usernames, availability checks,
  existing-centre students, bulk add, rename, resend and membership deactivation.
- Added the centre-scoped student directory and student Tests-home API. The demo centre is
  seeded with code `dtc`, five named student accounts and the shared demo password.
- Clean verification: 592 tests run, 551 passed and 41 optional PostgreSQL tests skipped;
  zero failures/errors. JaCoCo is 100% for instructions (17160/17160), branches
  (1143/1143), lines (3272/3272), complexity (1269/1269) and methods (690/690).
- Checkpoint A is complete. No V16 attempt/player work has started; Part 2 begins only
  after this checkpoint is committed and reported.

# Earlier progress — feature/publish (M4 branch 2)

## S1 — existing exam schema extensions (2026-11-02)

- Pulled both latest dev branches and created feature/publish. Backend dev contains
  branch 1. Frontend includes the existing branch-1 commits as a dependency while its
  PR remains separate; no PRs were merged by this work.
- V14 adds definition kind/pairing, decimal marks, frozen instance content/answer keys,
  publish settings, assigned-student snapshots, AI breakdowns and typed marking jobs.
- Reuses existing creator, item-order, camera and integrity columns. Published V1–V13
  migrations remain unchanged. Composite tenant FKs protect new snapshot/job links.
- Green: 21 migration, tenancy and exam-chain persistence tests passed.
- S2 green: 13 API/migration tests passed. Owned definition CRUD and decimal marks,
  grade/kind validation, atomic publish with full question and active-roster freezes,
  per-publish item selection/marks, current-assignment instance access, code regeneration,
  idempotent late adds and early close are implemented.
- V14 also freezes the definition name and kind so later definition edits cannot relabel
  earlier instances. Existing order/integrity/camera columns remain reused.
- S3 green: 31 API, migration, generation-claim and marking tests passed. The marking
  adapter supports Ollama/Gemini structured responses, validates exact criterion identity
  and score bounds, and persists a complete keyed breakdown only after validation.
- Marking jobs freeze provider/model/prompt/rubric/answer, isolate generation quotas and
  claim scans, retry transient failures only, reject stale completions and exhaust at two
  claims. A late result cannot overwrite a teacher confirmation. No answer text is logged.
- S4 green: 40 targeted/upgrade tests plus the full execution report pass the unchanged
  100% line/branch gates. Added authorization coverage, legacy V13→V14 backfill evidence,
  practice/kind boundaries, code-collision handling and current-roster account/role checks.
- Real PostgreSQL 16: 53 migration/review/publishing/marking tests passed, including new
  composite tenant FKs. Fresh databases now receive the required exam-mode catalogue rows.
- Publish, close and roster-assignment timestamps are canonicalized to PostgreSQL's
  microsecond precision. A committed-transaction regression proves the complete publish
  response is byte-for-byte stable when the instance is reloaded.
- Added the API/marking contract, updated settled scope, CI PostgreSQL suites, as-built ER
  metadata and full-stack publishing acceptance.
- Final clean verification: 582 tests run, 541 passed and 41 optional PostgreSQL tests
  skipped; 0 failures/errors. JaCoCo is 100% for instructions (16093/16093), branches
  (1023/1023), lines (3185/3185), methods (640/640) and classes (189/189).
- Fresh production-container acceptance passed migrations, nginx routing, authenticated
  M2 onboarding, M4 review/bank and the complete publish/instance lifecycle.
- Next: none on this branch — backend branch 2 is ready for review.

# Progress — feature/review-and-bank (backend)

## Published migration compatibility (2026-11-02)

- Remote refresh revealed backend PR #12 was merged before this request began. The
  remaining backend fixes will be delivered as a follow-up PR to dev.
- Preserve published V13, its checksum, existing revision column/indexes and the established
  revision mechanism. This supersedes the attempted V13 simplification in the earlier slice;
  existing databases can validate and start without a Flyway repair or schema rewrite.
- This follow-up introduces no schema changes. After restoring the published migration and
  revision mapping, `mvn -o -B clean verify` passed: 541 tests, 0 failures/errors, 23 optional
  PostgreSQL tests skipped (518 passed), 100% lines (2831/2831) and branches (759/759).
- A fresh PostgreSQL 16 database passed all 35 migration/review checks with the published V13.
  Frontend final evidence remains 374 passing tests with 100% coverage. The earlier numbers
  below are slice history; these are the final backend code/coverage results.

## Final coverage and database slice (2026-11-02)

- MCQ answer indices that overflow a Java integer are rejected by the shared generator/edit
  validator. Added boundary tests for written totals and matching immutable fields.
- Added real PostgreSQL tests for approver/source composite tenant foreign keys and the
  published V13 column/index contract.
- Final `mvn -o -B clean verify`: 541 tests, 0 failures/errors, 23 optional PostgreSQL
  tests skipped (518 passed). JaCoCo: 100% lines (2831/2831) and branches (759/759).
- Disposable PostgreSQL 16 run: all 35 migration/review checks passed, including the
  endpoint/role/state matrix and simultaneous-edit test.
- Full isolated Compose acceptance passed fresh migrations, production builds, nginx
  routes/proxy, M2 HTTP/SMTP and generation-to-review/bank/fork flows. Python evidence
  tooling: 3 tests passed. Frontend companion: 374 tests, all coverage gates passed.
- Open PRs target dev; no merge is part of this work. Browser automation is unavailable;
  frontend UI evidence is from Testing Library interactions and HTTP integration.

## Contract completion slice (2026-11-02)

- Audited the supplied branch-1 brief against the existing implementation. List responses
  now expose bounded summaries only; detail/mutations retain the complete staff payload.
- Forbidden type/pairing changes and invalid topic assignments return 400 LRN-VAL-001.
  Shared generation/edit validation bounds written rubric totals to 100 and checks declared totals.
- V13 adds only approval actor/time and fork lineage; concurrency reuses the existing version
  column and row lock. The centre/status index already exists in V3.
  This migration adjustment was superseded by the compatibility slice below.
- Green slice: 70 targeted tests passed, including an endpoint/role/state authorization matrix,
  generation/edit rejection parity and immutable fork checks for every type.
- Clean full verification and fresh PostgreSQL evidence will be recorded in the next slice.

## M4 branch 1 — review and bank (2026-11-02)

- Implemented creator/admin draft review, five question editors, shared validator errors,
  approval metadata, immutable approved content, private forks with lineage, paged bank
  filters and revision checks with transactional locking. Flyway V13 adds review metadata.
- Recorded all ten accepted policies in `docs/m4-requirements.md`, including highest
  confirmed score superseding task 69, flashcard separation and UTC scheduling scope.
- Verified: native `mvn -B verify` and Java 17 container `mvn -B clean verify` pass.
  Each reports 523 tests, zero failures/errors, 18 optional PostgreSQL
  checks skipped and all coverage gates passed. The separate real PostgreSQL run passes
  all 22 migration/review checks, including concurrent edits.
- Isolated Compose acceptance passes fresh migrations, production builds, onboarding,
  generation through an Ollama-compatible fixture, review/approval/bank/fork and tenancy
  boundaries. This fixture validates integration; it does not assess real model quality.
- Next: review/merge branch 1, then `feature/publish` for definitions, publishing, frozen
  roster/items, live controls and marking engine. Student players and marking UI stay M5.

# Earlier progress — feature/port-domain-model (backend)

## M0–M3 closure hardening (2026-11-02)

- CI failure fixed: cryptography failure tests now use scoped mocks rather than removing JVM-wide security providers, which could poison SSL initialization on Java 17. CI tests Java 17 and 25.
- Generation now executes the provider/model frozen on each job, even after the configured default changes. Added routing and worker-boundary regression tests.
- `learnium.api.errors` records MVC/security errors with bounded code/status tags; real-chain tests verify each error is counted once.
- Added isolated Compose HTTP/SMTP acceptance, a three-chapter teacher-review evidence collector/scorer, and an as-built V1–V12 ER diagram/exporter. Docker contexts exclude generated files and host dependencies.
- Verified: clean Maven verification on Java 25 and Docker Java 17 — 491 tests, no failures/errors, four optional PostgreSQL checks skipped, full coverage gate passed. Frontend retry UI fixed and verified: 332 tests, full coverage, build passed. Isolated full-stack acceptance passed.
- Acceptance and future grading scope are recorded in `docs/m0-m3-closure.md`. Teacher quality approval, brief UI/Grafana acceptance and green checks on the pushed commit remain release evidence.

- Done: M0 skeleton — Spring Boot 4 / Java 17 app, LRN error foundation,
  structured JSON logging + traceId filter, security baseline + ping endpoint,
  Flyway V1 baseline, Dockerfile + docker-compose + CI. `mvn verify` green:
  30 tests, JaCoCo LINE 100% (344/344), BRANCH 100% (10/10).
- Done: branch restructure per Nagendra (2026-10-28) — M0 commits moved from
  `dev` onto this branch; `dev` reset to a clean root commit so M0+M1 ship as
  ONE MR into dev.
- Next: M1 slices — S1 tenancy core (Centre/User/Batch/Membership + master
  data), S2 content (Topic/QuestionType/Material/Question), S3 exam chain +
  stats/gamification, S4 demo-centre seeder, S5 API conventions, S6 OTel
  instrumentation. Full suite + coverage gate at feature end (lean rule).
- Failing tests: none.
- Env note: JVM network blocked in this sandbox — Maven runs offline against
  the curl-seeded ~/.m2 (see ~/workspace/tools/seed_repo.py). Standard pom;
  plain `mvn verify` works on a normal machine / CI.
- M1 S1 done: tenancy core (Centre, UserAccount+roles, StudentProfile, Batch,
  BatchMembership roster, Grade/Subject/GradeSubject) + Flyway V2 with
  fixed-UUID master seed (G1..G12, 5 subjects, 60 pairings) + JpaAuditingConfig.
  Targeted persistence tests green (incl. centre-scoping proof). Migration
  test asserts V1..V4, so it goes green only at S3.
- M1 S2 done: content model (Topic, QuestionType with code, Material, Question
  with jsonb payload + DRAFT/APPROVED/REJECTED) + Flyway V3 with the five
  question-type seeds. Targeted persistence tests green.
- M1 S3 done: exam chain (ExamMode, ExamTemplate(+Question), ExamInstance with
  NEW published-test semantics, ExamInstanceQuestion, ExamAttempt with
  attempt_no + INVALIDATED, QuestionAttempt with theory marking) +
  UserTopicStat/Badge/UserBadges/UserXpLedger + Flyway V4. Migration test now
  fully green: V1..V4 apply in order on H2-PG mode and ddl-auto=validate
  passes against the migrated schema. Also fixed an M0 gap the migration test
  caught: Boot 4 needs spring-boot-starter-flyway (bare flyway-core registers
  no auto-configuration, so migrations never ran in-app).
- M1 S4 done: DemoCentreSeeder (@Profile demo, idempotent) — Demo Tutoring
  Centre, admin + Mrs. Patel (Demo@123), G8 Mathematics pairing from master
  data, Fractions topic, Batch B, 5 students (Simran first) with BCrypt PIN
  1234; BCrypt PasswordEncoder bean added to SecurityConfig. Seeder tests
  green against the migrated schema.
- M1 S5 done: PageResponse<T> API envelope (tracker task 55) with unit tests.
- M1 S5/S6 done: OTel instrumentation (tracker task 54) — Boot 4
  spring-boot-starter-opentelemetry, env-driven OTLP tracing/metrics export
  (empty endpoint = no export), verified by a context test with the endpoint
  set. Note: @SpringBootTest disables metrics export by default in tests.

- M1 COMPLETE: full `mvn -o verify` green — 50 tests, JaCoCo LINE 100%
  (221/221), BRANCH 100% (14/14), gate checks met. Slices: S1 tenancy core,
  S2 content, S3 exam chain + gamification, S4 demo seeder, S5 PageResponse,
  S6 OTel export. Next: M2 (auth & roles). Failing tests: none.

- PR fix (2026-10-28): dev root merged into this branch (--allow-unrelated-histories) so GitHub accepts the PR; dev tree was empty, no content change.

## Codex review fixes (2026-10-28) — in progress
- Fix S1 done: Flyway V5 tenancy invariants — centre_id backfilled + NOT NULL
  on the 10 child tables, composite FKs (centre_id, parent) make cross-centre
  links unstorable, template uniqueness per centre (NULL topic_id caveat noted
  in the migration; service layer guards it in M4), exam_modes UNIQUE
  (test_type). Entities carry a plain centreId set from the owning parent;
  seeder + fixtures updated. New TenancyInvariantTest: 7 tests on the migrated
  schema (cross-centre writes rejected, cross-centre template names coexist,
  same-centre duplicate rejected, duplicate mode type rejected). Targeted
  tests green: migration + invariant + tenancy/content/exam/seeder suites.
- Fix S2 done: every tenant repository finder is centre-scoped
  (findByCentreIdAnd…): roster finders/counts, template questions, instance
  questions, attempts, question attempts, topic stats, badges, XP ledger.
  findByJoinCode stays global BY DESIGN (join-code discovery; tenancy is
  established from the returned instance before any further read — javadoc
  says so). Batch/UserAccount/Question/Material/ExamTemplate finders were
  already scoped; Topic finders key on global curriculum (grade-subject), not
  tenant data. TenancyPersistenceTest de-vacuumed: the foreign student now
  holds a membership in their OWN centre, and batches are seeded in both
  centres with disjoint assertions. ContentPersistenceTest page test now
  asserts every row's owner + disjoint ID sets across centres.
  ExamChainPersistenceTest gains answersAreInvisibleAcrossCentres (real
  foreign attempt id under the caller's centre → empty). Targeted tests green.
- Fix S3 done: OTel export properties are the real Boot 4 ones, verified
  against spring-configuration-metadata.json inside the installed Boot
  4.0.0 jars — tracing binds
  `management.opentelemetry.tracing.export.otlp.endpoint` (the old
  `management.otlp.tracing.endpoint` is an unbound deprecation relocation;
  the exporter auto-config conditions on the new name), metrics binds
  `management.otlp.metrics.export.url` (+ step). ObservabilityConfigTest
  now proves BINDING (OtlpTracingProperties + OtlpTracingConnectionDetails
  URL + OtlpConfig URL), and a new ObservabilityNoExportTest proves
  endpoint-unset creates no connection details/exporter.
  DEVIATION (evidence-driven): the endpoint is no longer a YAML placeholder.
  Binding the corrected property name to `${...:}` (empty) — or to any
  literal placeholder — makes @ConditionalOnProperty treat it as present
  and the OTLP exporter dies at startup ("Invalid endpoint"). Instead,
  OtelEndpointEnvironmentPostProcessor (registered via META-INF/
  spring.factories — the .imports file is NOT consulted for this factory
  type in Boot 4.0.0; proven by loader probes) maps
  OTEL_EXPORTER_OTLP_ENDPOINT to both export properties only when set,
  and explicitly sets management.otlp.metrics.export.enabled=false when
  unset (otherwise the meter registry falls back to localhost:4318 and
  "no export" is false for metrics). Endpoint value is the collector BASE
  (http://host:4318); exporters append their signal paths. Verified
  end-to-end with the packaged jar on H2: no env → healthy, zero OTLP
  activity; env set → OtlpMeterRegistry publishes to the base URL.
- Fix S4 done: security rejections now return the standard envelope.
  TraceIdFilter is @Order(HIGHEST_PRECEDENCE) so it precedes the security
  chain (rejected requests get trace ids + echoed X-Request-Id — verified
  through the full MockMvc chain with supplied and generated ids).
  ApiAuthenticationEntryPoint (LRN-AUTH-001) and ApiAccessDeniedHandler
  (LRN-AUTH-003) write ApiErrorResponse JSON via a shared
  SecurityErrorResponseWriter using the context ObjectMapper (Jackson 3 in
  Boot 4) and the MDC trace id; HttpStatusEntryPoint removed;
  @EnableMethodSecurity on. Two findings while testing: (1) @PreAuthorize
  denials surface inside the DispatcherServlet, past ExceptionTranslation
  Filter, so GlobalExceptionHandler also translates AccessDeniedException
  to the same 403 envelope; (2) once the tracing bridge starts a server
  span it owns the MDC "traceId" slot, so an envelope's traceId is the
  active span id (uniform with controller errors) while X-Request-Id
  remains the client correlation id echoed in the header.
- Fix S5 done: framework request exceptions keep their real status in the
  standard envelope instead of collapsing to 500/LRN-SYS-001 —
  HttpMessageNotReadableException → 400 LRN-VAL-002 (WARN, no stack),
  HttpRequestMethodNotSupportedException → 405 LRN-SYS-405,
  HttpMediaTypeNotSupportedException → 415 LRN-SYS-415. New ErrorCode
  entries follow the registry pattern; covered by handler unit tests and
  MockMvc tests through the test-support controller (malformed JSON /
  wrong method / wrong content type, all authenticated).
- Fix S6 done: PostgresMigrationTest (env-gated on LEARINUM_PG_TEST=true;
  datasource from LEARINUM_PG_URL/USERNAME/PASSWORD with localhost
  defaults) asserts ≥5 successful Flyway migrations on real PostgreSQL, a
  Centre JPA round-trip under ddl-auto=validate, and that a raw
  cross-centre membership INSERT violates the V5 composite FK. Verified
  locally: SKIPPED (3/3) without the env var, default suite unaffected.
  CI gains a `postgres-migration` job (postgres:16 service with health
  check, JDK 17, runs only that test class). Workflow YAML validated.
- CODEX REVIEW FIXES COMPLETE (S1–S6). Final full `mvn -o verify` green:
  86 tests run (83 executed, 3 skipped — the env-gated PostgresMigrationTest),
  0 failures; JaCoCo LINE 100% (283/283), BRANCH 100% (20/20), all coverage
  checks met. Ready to re-bundle for Nagendra's push; PR #7 updates in place.
- Failing tests: none.
- CODEX FOLLOW-UP ROUND 2 (2026-10-28): Nagendra approved fixing ONLY
  finding #2 (content tenancy) from the follow-up review; the remaining
  items are deferred by his decision — repository-access discipline becomes
  an M2 build rule, malformed-body log redaction + the 405 Allow header go
  to M2, and the OTLP signal paths (/v1/traces, /v1/metrics) go to Release
  hardening. M0/M1 foundation reviews are closed after this round.
  Fix done (e29ecc4): Flyway V6__content_tenancy_fks.sql —
  UNIQUE (centre_id, material_id) on materials; composite FK
  questions (centre_id, material_id) -> materials (centre_id, material_id);
  composite FK materials (centre_id, uploaded_by) -> user_account
  (centre_id, user_id). V3 single-column FKs kept, as in V5; no entity
  changes (the associations already existed; enforcement is DB-level).
  Tests: TenancyInvariantTest gains cross-centre question->material and
  material->uploader negatives (both fail at flush) plus the same-centre
  positives (11/11 green); MigrationTest now expects V1–V6;
  PostgresMigrationTest gains a raw cross-centre question INSERT negative
  for real-PostgreSQL CI coverage and its migration-count floor rises to 6
  (class still env-gated: 4 skipped locally). Final full `mvn -o verify`
  green: 91 tests run (87 executed, 4 skipped — the env-gated
  PostgresMigrationTest), 0 failures; JaCoCo LINE 100% (283/283),
  BRANCH 100% (20/20). Ready to re-bundle for Nagendra's push; PR #7
  updates in place.
- Failing tests: none.

## M2 — auth & roles (feature/auth-roles)
- M2 S0 done: carried warm-up fixes from the Codex follow-up review.
  (a) GlobalExceptionHandler's malformed-body handler no longer logs the
  parser exception message (Jackson messages quote the submitted body);
  it logs only the error code, trace id and exception class name. New
  MalformedBodyLoggingTest (OutputCaptureExtension) posts a malformed body
  carrying a sensitive marker and asserts the marker never reaches the
  logs while the 400/LRN-VAL-002 envelope still returns.
  (b) The 405 handler now preserves the framework headers from
  HttpRequestMethodNotSupportedException (Allow: <supported methods>);
  asserted in both the handler unit test and the MockMvc 405 test.
  Targeted tests green: GlobalExceptionHandlerTest + SystemControllerTest
  + MalformedBodyLoggingTest (23 tests).
- Next: M2 S1 — auth core (Flyway V7 auth tables, JWT service + filter,
  refresh-token rotation, login/refresh/logout/me).
- Failing tests: none.
- M2 S1 done: auth core. Flyway V7 — account_token, email_outbox,
  refresh_token (all centre-owned with composite FKs) +
  batch_memberships.pin_failed_attempts/pin_locked; PostgresMigrationTest
  floor raised to 7. NOTE: token_hash is varchar(64), not char(64) —
  Hibernate schema validation distinguishes CHAR from VARCHAR; same
  fixed-length hex content. JwtService (jjwt, HS256; key = SHA-256 of the
  configured secret so any secret length yields a 256-bit key; blank secret
  falls back to a labelled dev-only value) issues 15-min staff tokens and
  12-h student tokens (batchId + membershipId claims). JwtAuthenticationFilter
  populates LearniumPrincipal (userId, centreId from token, role); permit
  list covers the auth doors + /public/**. Refresh tokens are 32-byte
  random, SHA-256-hashed at rest, rotating; replaying a revoked token
  revokes the user's whole family (the bulk revoke uses
  clearAutomatically — a JPQL update otherwise leaves stale live tokens in
  the persistence context). Login: global email lookup, password picks the
  account; unknown/wrong/ambiguous -> identical LRN-AUTH-002; inactive with
  matching password -> LRN-AUTH-005. New ErrorCodes AUTH-002/004/005/006/
  007, USR-001/002, CTR-001/002. pom.xml gains jjwt 0.12.6 + mail +
  thymeleaf starters (S2 uses the latter two). Targeted green: JwtServiceTest,
  CurrentPrincipalTest, AuthFlowTest (18), MigrationTest, ErrorCodeTest.
- Next: M2 S2 — account tokens + email outbox + invite/reset flows.
- M2 S2 done: account tokens + email outbox + invite/reset flows.
  AccountTokenService issues single-use tokens (raw returned once, SHA-256
  hash stored) and consumes them (unknown/used/expired -> LRN-AUTH-004, same
  response for all). EmailOutboxService.enqueue writes a PENDING row inside
  the caller's transaction; OutboxDispatcher (@Scheduled, 5s default)
  renders + sends in one transaction, retrying FAILED rows until 3 attempts;
  last_error stores the exception class name only. Mail renders via a
  standalone Thymeleaf engine — DEVIATION: SpringTemplateEngine (SpringEL),
  because the standalone engine's OGNL evaluator is not on the classpath
  (NoClassDefFoundError ognl/PropertyAccessor). Templates invite/reset link
  to {APP_PUBLIC_BASE_URL}/set-password?token=..&purpose=..; HTML + derived
  plain-text bodies; transport behind EmailSender (SMTP impl; tests use a
  stub). forgot-password always 202 (unknown/inactive/student stay silent);
  reset revokes ALL refresh tokens of the user; accept-invite sets the
  first password. BUG CAUGHT BY TESTS: the refresh-token bulk revoke uses
  clearAutomatically, which discarded a caller's unflushed password change
  during reset (old password kept working) — the @Modifying query now also
  sets flushAutomatically=true. Targeted green: AccountTokenServiceTest,
  AccountFlowTest (9), OutboxDispatcherTest, MailTemplateRendererTest,
  SmtpEmailSenderTest.
- M2 S3 done: teacher admin (global email uniqueness -> 409 LRN-USR-001;
  create with null password + INVITE + outbox row; paged centre-scoped list;
  deactivate = DISABLED + refresh-token revocation; foreign/non-teacher ids
  -> 404 LRN-USR-002). Batches: create (unknown gradeSubjectId -> 400),
  gradeLabel "Grade 8 · Mathematics"; TEACHER ASSIGNMENT MODELLED AS A
  BatchMembership ROW with memberRole=TEACHER (the M1 model already had the
  role field) — reactivation supported, a STUDENT membership for the same
  user/batch blocks assignment with 400; unassign requires an ACTIVE teacher
  membership else 404 LRN-CTR-002; GET /batches: admin sees all centre
  batches, teacher only assigned ones; studentCount = ACTIVE STUDENT
  memberships. Centre profile GET (staff) / PUT (admin, @URL logoUrl +
  @Email contactEmail validated, nulls allowed). Platform onboarding:
  POST /api/v1/platform/centres is permit-listed with the X-Provisioning-Key
  header as the credential — no key configured -> 404 LRN-SYS-404,
  wrong/missing key -> 401 LRN-AUTH-001 (constant-time compare), correct ->
  centre + CENTRE_ADMIN + INVITE enqueued, duplicate admin email -> 409.
  Master grade-subjects (staff) and public centres/batches (names only,
  batches active + name-sorted, unknown centre -> empty list by design).
  BatchAccessGuard centralises batch tenancy (404) + teacher assignment
  (403). Targeted green: AdminFlowTest (17), PlatformOnboardingTest (4),
  PlatformOnboardingDisabledTest (2).
- M2 S4 done: roster management under /api/v1/batches/{batchId}/students
  (BatchAccessGuard on every call: admin any centre batch, teacher assigned
  only). Add creates UserAccount (STUDENT, no email/password) + StudentProfile
  (grade from the batch's GradeSubject) + ACTIVE membership with a BCrypt
  hash of a SecureRandom 4-digit PIN; the raw PIN is returned once in the
  response DTO only. Bulk add is one transaction, request order preserved,
  names trimmed, blank names -> 400 via element Bean Validation. Reset-pin
  issues a fresh PIN and clears attempts/lock; deactivate flips the
  membership to INACTIVE (the roster list still shows it, with status).
  Student session: batch id is the door key (global batch lookup by design;
  everything after is scoped to that batch); ACTIVE roster matched by
  trim + case-insensitive display name; unknown name and wrong PIN share
  the generic 401 LRN-AUTH-007; wrong PINs increment attempts on every
  matched membership and the 5th failure locks; a fully locked match set
  gets 403 LRN-AUTH-006 even with the correct PIN; success resets the
  counters and issues the 12-h student JWT (batchId + membershipId claims).
  Same-name students are disambiguated by PIN. Targeted green:
  RosterFlowTest (8), StudentSessionTest (9).
- M2 COMPLETE: cross-cutting done (docker-compose backend env gains a
  clearly-labelled dev-only JWT_SECRET, APP_PUBLIC_BASE_URL and MAIL_HOST
  pointing at the Mailpit service; README documents the M2 env vars and the
  now-working demo login admin@demo.learnium.local / Demo@123 + roster PIN
  1234). Final full verify from a clean target: 214 tests run, 0 failures,
  4 skipped (env-gated PostgresMigrationTest), JaCoCo LINE 100% and BRANCH
  100% (zero missed lines/branches in the report), all coverage checks met.
  Getting there took a coverage-hardening pass after the first clean verify
  exposed real gaps the slice-level targeted runs had hidden (198 tests,
  branches 94%): the two @SneakyThrows SHA-256 helpers (JwtService.deriveKey,
  TokenHashing.sha256Hex) were replaced with explicit try/catch ->
  IllegalStateException, exercised by tests that remove the SUN security
  provider; LearniumPrincipal.isStudent, the services' centre-not-found
  orElseThrow suppliers (mock-based unit tests), AuthService.me's missing-
  account path, PlatformService's null-key path, JwtService's null-secret
  fallback and half-scoped student tokens, the inactive-teacher DTO branch,
  unassign-for-a-student 404, and double-logout all gained tests (+16).
  NOTE: incremental verifies in this sandbox are unreliable for coverage —
  target/jacoco.exec accumulates sessions across recompiles and both
  phantom misses and phantom passes appear; only a from-scratch verify
  (rm -rf target first; mvn clean is unavailable offline) is authoritative.
  Failing tests: none. Ready for Nagendra's review; do not push (bundle
  handoff per the working agreement).

- FIX ROUND (Codex M2 review, request changes) F1 done: JWT secret is now
  bound from JWT_SECRET in application.yml (was a hardcoded dev literal);
  JwtService uses the dev fallback ONLY behind a gate — demo/dev profile or
  JWT_DEV_SECRET_ALLOWED=true — and fails startup otherwise (a configured
  value equal to the dev literal counts as no real secret). Test profiles
  carry a test-only secret. README env table updated. Targeted green:
  JwtServiceTest (16, incl. gate matrix), MigrationTest, DemoCentreSeederTest,
  LearniumApplicationTests.
- Next: F2 transaction correctness (PIN lockout, refresh rotation/replay,
  account-token atomic consume). Failing tests: none.

- FIX ROUND F2 done: transaction correctness (Codex P1-2..P1-5). PIN
  failures now commit via a REQUIRES_NEW atomic increment+lock update, so
  the lockout persists across requests; refresh rotation consumes the
  presented token with one conditional update (a concurrent loser is
  treated exactly like a replay) and family revocation — on replay, on
  race loss, and from revokeAllForUser — always commits in an independent
  transaction, so a following rejection can no longer roll it back;
  invite/reset tokens are consumed by a conditional update inside the
  password-change transaction (exactly one concurrent winner). AuthService
  .refresh is deliberately non-transactional (orchestrates the above).
  Test rework started (rest lands in F4): AuthFlowTest, AdminFlowTest and
  StudentSessionTest converted to committed-state (NOT_SUPPORTED +
  CommittedDataCleanup) — their lockout/replay/deactivation assertions now
  observe production transaction behaviour. Targeted green: AuthFlowTest
  (19), AdminFlowTest (21), StudentSessionTest (10), AccountTokenServiceTest
  (5). One find along the way: the rotation read must fetch-join the user's
  centre as well, or it arrives as an uninitialized proxy.
- Next: F3 P2 batch (V8 email uniqueness, outbox claim + payload cipher,
  filter status checks, resend invite). Failing tests: none.

- FIX ROUND F3 done: P2 batch. V8 is a JAVA migration
  (V8__GlobalStaffEmailUniqueness): a lower(email) expression index cannot
  be expressed for both engines (H2 2.4.240 rejects the expression index;
  the generated-column syntax differs — PostgreSQL requires STORED, H2
  rejects it) and Flyway does not filter vendor subfolders here, so the
  migration branches on the connection's database product and adds an
  email_normalized generated column + global unique index; emails are
  normalised (trim + lower) at login/forgot/create-teacher/create-centre,
  with flush violations translated to the registry's email-in-use code
  LRN-USR-001. Outbox: PayloadCipher (AES-GCM over the payload model; key =
  SHA-256 of OUTBOX_ENCRYPTION_KEY, else the JWT secret material, else the
  dev fallback) encrypts on enqueue; the dispatcher claims rows with a
  conditional status flip (PENDING/FAILED/SENDING-stale -> SENDING,
  5-minute stale window), decrypts on delivery and purges the payload to
  '{}' on SENT and on terminal FAILED. JwtAuthenticationFilter now checks
  the subject's CURRENT status on every request (staff account /
  membership projection): inactive -> 403 LRN-AUTH-005 from the filter,
  missing subject -> unauthenticated. Resend invite:
  POST /api/v1/admin/teachers/{id}/resend-invite invalidates outstanding
  invite tokens and enqueues a fresh 48h invite in one transaction;
  TeacherDto gains `invited` (passwordHash == null). AccountFlowTest
  converted to committed state as part of this slice. Targeted green:
  PayloadCipherTest (7), EmailAddressesTest (3), JwtAuthenticationFilterTest
  (9), V8MigrationTest (2), MigrationTest (5, incl. cross-centre case-variant
  rejection on the real migrated schema), OutboxDispatcherTest (10),
  AuthFlowTest (21), AdminFlowTest (25), StudentSessionTest (12),
  AccountFlowTest (9), TeacherAdminServiceTest (2), PlatformServiceTest (2),
  RosterFlowTest (10), PlatformOnboardingTest (4) + Disabled (2),
  DemoCentreSeederTest (2). One test-authoring bug found and fixed on the
  way: the stale-SENDING test must flush before backdating updated_at, or
  the auditing listener overwrites the backdate at the next auto-flush.
- Next: F4 committed-state concurrency tests + final from-scratch verify.
  Failing tests: none.

- FIX ROUND F4 done: CommittedAuthStateTest (committed state, real
  services + HTTP) proves the production transaction behaviour end to end:
  (1) five failed PIN attempts persist across separate requests and the
  6th call with the correct PIN gets the locked 403; (2) after a refresh
  replay, the family revocation is committed — the successor token can no
  longer refresh and both rows show revokedAt set; (3) two concurrent
  refreshes of one token (latch-gated threads) -> exactly one success, and
  the loser's replay handling leaves the winner's successor revoked too;
  (4) two concurrent resets with one token -> exactly one password change
  (verified against the stored hash). 4/4 green. Note: AccountFlowTest had
  already been converted in F3, completing the committed-state rework of
  all four auth-flow test classes.
- Final gate, first pass: 261 tests, 0 failures, 4 skipped (PG-gated);
  JaCoCo gate FAILED at 99% lines / 98% branches — PayloadCipher's two
  defensive catches (SHA-256 / cipher provider unavailable) and the
  dispatcher's claim-lost branch were untested. Hardening added:
  provider-removal tests mirroring the TokenHashing pattern, and the
  mock-based OutboxDispatcherClaimTest (claim lost -> no delivery;
  claimed row vanished -> skipped). Targeted green.
- FIX ROUND COMPLETE: final from-scratch verify (rm -rf target +
  mvn -o verify) BUILD SUCCESS — 265 tests run, 0 failures, 0 errors,
  4 skipped (env-gated PostgresMigrationTest); JaCoCo LINE 100%
  (1208/1208), BRANCH 100% (195/195), INSTRUCTION 100% (5145/5145),
  zero missed in jacoco.csv totals. Commits on feature/auth-roles:
  0b9e812 (F1), 502eee3 (F2), e93689d (F3), ca79880 (F4), a26dad8
  (coverage hardening). Not pushed — bundle handoff per the working
  agreement; backend PR #8 updates in place once Nagendra pushes.

## Fix round 2 (Codex rerun, 2026-10-28)
- R2-S1 done: secret audit of the configuration. spring.mail.password was a
  hardcoded literal (and contradicted the README, which documents
  MAIL_PASSWORD as blank-default) — now bound as ${MAIL_PASSWORD:}.
  spring.datasource.password was env-bound but carried the published local
  credential as its default — now ${DB_PASSWORD:} with no default, so a
  forgotten value fails fast (the README run-locally flow exports it and
  docker compose passes it to the backend service, so local flows are
  unchanged). Rest of the audit: JWT secret, outbox encryption key and the
  platform provisioning key were already env-bound with empty defaults;
  MAIL_USERNAME already empty-default; DB_USERNAME/host/port/URL/from
  defaults are non-secret local-dev values; the test-profile YAMLs hold
  only H2/test-fixture credentials by design.
- Next: R2-S2 security version (reset/login race). Failing tests: none.
- R2-S2 done: security version (reset/login race, Codex rerun P1). Flyway
  V9 adds security_version (bigint, NOT NULL, default 0) to user_account
  and refresh_token. RefreshTokenService.issue stamps the token with the
  account's current version; password set/reset (AccountFlowService
  accept-invite + reset-password) and teacher deactivation increment the
  version in the same transaction as the credential/status change (the
  revoke-all stays). rotate() refuses a stamp mismatch with the usual 401
  after killing just that token in a REQUIRES_NEW transaction — a version
  mismatch is not a replay, so no family action (the credential change
  already revoked the family it knew). MigrationTest now expects V1..V9.
  Targeted green: MigrationTest (5), V8MigrationTest (2), AuthFlowTest
  (21), AccountFlowTest (9), AdminFlowTest (25), AuthServiceTest (1),
  TeacherAdminServiceTest (2).
- Next: R2-S3 committed-state proofs for the security version. Failing
  tests: none.
- R2-S3 done: CommittedSecurityVersionTest (committed state, real
  services) proves the version behaviour: (a) a token row persisted
  AFTER a committed reset, stamped with the pre-reset version — the
  deterministic form of the racing login — is refused at refresh and
  the refusal itself revokes it in committed state; (b) login +
  repeated rotation are unaffected while versions match (all rows carry
  the current stamp); (c) after a reset the old password fails, a new
  login works and its token is stamped with the post-reset version and
  refreshes; (d) deactivation bumps the version on top of the existing
  revoke-all, and a stale-stamped token landing after it is refused the
  same way. 4/4 green.
- Next: final from-scratch verify for the round. Failing tests: none.
- FIX ROUND 2 COMPLETE: final from-scratch verify (rm -rf target +
  mvn -o verify) BUILD SUCCESS — 269 tests run, 0 failures, 0 errors,
  4 skipped (env-gated PostgresMigrationTest); JaCoCo LINE 100%
  (1217/1217), BRANCH 100% (197/197), INSTRUCTION 100% (5193/5193),
  zero missed in jacoco.csv totals. Commits on feature/auth-roles:
  2794576 (R2-S1 config bindings), 8805542 (R2-S2 security version),
  e27440c (R2-S3 committed-state proofs). Not pushed — bundle handoff
  per the working agreement; backend PR #8 updates in place once
  Nagendra pushes.

# Progress — feature/pdf-extraction (backend, M3 branch 1)

- Setup: branched off merged dev f63ec5f (PR #8 merge; contains V9 +
  security version + both M2 fix rounds). PDFBox was NOT in the pom —
  added pdfbox 3.0.5 + AWS SDK v2 s3 (BOM 2.29.45); offline repo seeded
  via seed_repo.py (resolution complete; script's final verify raced
  in-flight code, artifacts all present).
- Done (S1): Flyway V10 (materials += content_hash, extraction_status,
  extracted_chars, deleted_at — extracted_text/storage_key already
  existed from M1/V3). StorageService + local impl (path-traversal
  safe) + S3 impl (mocked client tests) selected by
  learnium.storage.type. MAT error codes LRN-MAT-001/002/003.
  MigrationTest now expects V1..V10 (H2 note: use `timestamp with
  time zone`, H2 rejects the `timestamptz` alias).
- Done (S2): ExtractionService — PDFBox per-page extraction with a
  time budget; classification OK / NO_TEXT / UNUSABLE_TEXT (min chars,
  avg chars/page, alphanumeric ratio) / OVER_LIMIT (char + estimated
  token caps, truncate at char cap) / EXTRACTION_FAILED. PDF fixtures
  generated in test code (text / blank / header-only / encrypted).
  Targeted: storage 13 + extraction 12 + migration 5 green.
- Next: S3 materials API + flow tests, then the from-scratch verify.
- Failing tests: none.
- Done (S3): materials API + flow tests (13 + 1 limits + handler unit).
  Gotcha recorded: pre-assigning the UUID made save() take the merge
  path and trip @Version (StaleObjectStateException) — persist first,
  store the file in the same transaction instead.
- BRANCH 1 COMPLETE: final from-scratch verify (rm -rf target +
  mvn -o verify) BUILD SUCCESS — 318 tests run, 0 failures, 0 errors,
  4 skipped (env-gated PostgresMigrationTest); JaCoCo zero missed
  lines (1501), branches (271) and instructions in jacoco.csv.
  Commits on feature/pdf-extraction: db73b59 (S1), 93b825d (S2),
  32052a1 (S3), 27cdabb + 2f1c7cd (coverage closes). Not pushed —
  bundle handoff per the working agreement. Deferred to branch 2:
  physical cleanup of soft-deleted files (findCleanupCandidates),
  generation jobs. PostgreSQL validation of V10 rides CI on push.
- Follow-up fix: uploads are gated on the %PDF- magic bytes (not the
  filename) — non-PDF bytes now reject 400 LRN-MAT-001 before anything
  is extracted, stored or persisted; a valid header with a corrupt
  body still persists as EXTRACTION_FAILED. Targeted tests green
  (MaterialFlowTest 18, MaterialServiceTest 4, ExtractionServiceTest
  13); full re-verify by the parent from scratch.

## Codex review fix round — M3 branch 1 (2026-10-28)
- F-1 done: Dockerfile creates /app/data/files owned by learnium before
  the USER switch; compose mounts the named `appfiles` volume at
  /app/data so uploads survive container replacement.
- F-5/F-6/F-7/F-8 (extraction) done: page cap (learnium.extraction
  .max-pages, default 40) rejects BEFORE extraction with no partial
  work recorded (text/chars/hash NULL). Parsing runs on a dedicated
  daemon executor; the caller waits at most the budget — a per-page
  check alone cannot bound document load or one pathological page.
  Text accumulates through a bounded sink: at most the char cap is
  retained, every persisted outcome is truncated at the cap, and the
  content hash digests the COMPLETE text as it streams (prefix-twin
  chapters no longer share an identity). ExtractionServiceTest 22
  green; flow suites unchanged-green.
- Next: F-3 dedupe identity (V11) + race recovery; then the
  from-scratch verify. Failing tests: none.
- F-3 done: V11 adds an app-maintained dedupe_hash (mirrors content_hash
  while active, cleared on soft delete in the same transaction) with a
  plain unique index on (centre_id, uploaded_by, dedupe_hash) — NULLs
  never collide, portable to H2 + PostgreSQL (partial indexes are not).
  Creation moved to MaterialCreationService so a lost race rolls back in
  its own transaction; MaterialService recovers by returning the winner
  with deduplicated=true (never a 500). New committed-state
  MaterialConcurrencyTest runs on a dedicated Flyway profile (the test
  profile builds schema from entities, where migration-only indexes do
  not exist): latch-gated simultaneous pastes -> exactly one row, both
  callers served; re-upload after soft delete creates fresh; prefix-twin
  chapters (210K shared prefix, cap 200K) do not dedupe.
- Next: from-scratch full verify. Failing tests: none.
- FIX ROUND COMPLETE: from-scratch verify (rm -rf target + mvn -o
  verify) BUILD SUCCESS — 345 tests run, 0 failures, 0 errors, 4
  skipped (env-gated PostgresMigrationTest); JaCoCo zero missed lines
  (1602), branches (293) and instructions. Coverage closes: page-cap
  outcome moved out of the try-with-resources (synthetic close
  branches), the sink's digest factory moved to TokenHashing with a
  provider-removal test mirroring sha256Hex's, and the result record's
  convenience constructor got null/blank arm tests. Commits:
  6042f63 (F-1), 3e8511c (F-5..F-8 extraction), 874ffde (F-3), plus
  this coverage close. Not pushed — bundle handoff per the working
  agreement. PostgreSQL validation of V11 rides CI on the PR (the
  migration-profile suite proves V1..V11 on H2-PG mode locally).

## M3 branch 2 — feature/ai-generation (off dev c7277dcf, V1–V11 merged)
- S1 done: Flyway V12 — background_job (frozen job inputs, claim
  token/generation + lease, per-type result counts, token usage) with
  due/material indexes and composite tenancy FKs; questions gain
  generation provenance columns + composite FK to the job. BackgroundJob
  entity + status/failure-kind/difficulty enums; MigrationTest asserts
  V1–V12 and the cross-centre provenance FK rejection.
- Next: S2 job creation + read APIs. Failing tests: none yet.
- S2 done: LRN-GEN-001..005; GenerationProperties/Config (provider,
  worker, lease/backoff, daily caps; shared Clock bean). POST
  /materials/{id}/generate freezes the job (hash, counts, difficulty,
  provider/model, contract versions, creator) with the validation
  matrix + per-teacher/per-centre daily caps; GET /generation-jobs/{id}
  and GET /materials/{id}/questions (paged, newest first) under the
  branch-1 visibility rule. GenerationFlowTest 11 + GenerationCapFlowTest
  1 green. CommittedDataCleanup learned questions/background_job/topics.
- Next: S3 generator SPI + prompt/schema + Gemini/Ollama providers.
  Failing tests: none.
- S3 done: QuestionGenerator SPI + GenerationOutcome (typed failures),
  PromptBuilder (strict, only-from-text, exact counts), the shared
  structured-output schema, a ModelTransport seam with a thin
  HttpClient impl, and Gemini (structured output, finishReason +
  usageMetadata mapping incl. thoughts tokens) / Ollama (format schema,
  eval-count usage) adapters behind learnium.generation.provider.
  27 offline unit tests against recorded response shapes; no sockets.
- Next: S4 validation + draft factory. Failing tests: none.
- S4 done: DraftQuestionFactory (pure) validates per type with drop
  reasons (SCHEMA_INVALID, ANSWER_KEY_INVALID, RUBRIC_INVALID,
  FIELD_TOO_LONG, DUPLICATE_STEM, EXCEEDS_REQUESTED), caps, and
  materializes payload + correctRef following the M1 conventions:
  option ids a..d in the payload + correctRef = option id; FIB
  correctRef = the answer; NUMERIC correctRef = compact
  value/tolerance/unit JSON (correct_ref is varchar(50)); WRITTEN
  rubric criterion ids crit-1..n in the payload, correctRef =
  "modelAnswer". 17 unit tests green.
- Next: S5 worker. Failing tests: none.
- S5 done: claim-ownership worker. BackgroundJobRepository gains the
  fetch-joined claim snapshot and four conditional updates (claim /
  completeSucceeded / completeFailed / reschedule) keyed on id +
  status RUNNING + claim token + claim generation — a stale worker's
  completion matches zero rows. ClaimedJob snapshot; GenerationClaimService
  (claim, reschedule with backoff base × attempts, terminal fail with
  optional counts/usage); GenerationPersistenceService (material hash /
  deletion re-check, ownership-checked SUCCEEDED transition and question
  inserts in ONE transaction; difficulty bands EASY→1 / MEDIUM→3 /
  HARD→5 onto the 1..5 question scale; createdBy = the job's creator);
  GenerationWorker (retryable MODEL_ERROR / MALFORMED_RESPONSE /
  TRUNCATED reschedule while attempts < 2, everything else terminal;
  ZERO_VALID terminal with counts + usage; persist crashes leave the
  job RUNNING for lease reclaim); GenerationScheduler behind
  worker-enabled. 13 tests green, incl. the deterministic stale-worker
  test (blocking fake generator + mutable clock), deletion during
  generation, crash-before-commit via a spy seam (drafts exactly once
  after reclaim), retry/backoff timing and claim exclusivity.
- Next: S6 ER refresh + from-scratch verify. Failing tests: none.
- S6 done: ER-Diagram.md refreshed to the V1–V12 reality (BACKGROUND_JOB
  full shape, question provenance columns, material dedupe/extraction
  columns, security_version, REFRESH_TOKEN, composite-FK tenancy notes).
- BRANCH 2 BACKEND COMPLETE: final from-scratch verify (rm -rf target +
  mvn -o verify) BUILD SUCCESS — 459 tests run, 0 failures, 0 errors, 4
  skipped (env-gated PostgresMigrationTest); JaCoCo zero missed lines
  (2509), branches (603) and instructions (10904) in jacoco.csv.
  Coverage closes worth remembering: the read DTOs were only reachable
  with real drafts present (new flow tests seed finished jobs +
  questions); the RefreshTokenService race-loser arm was racy under the
  threaded test and is now pinned by a deterministic Mockito test
  (RefreshTokenRaceTest); three unreachable defensive arms were removed
  instead of contorted around (counts forRequest, the two writeJson
  catches, toWebJson's catch — in-memory tree serialization cannot
  fail). Commits on feature/ai-generation: cef85bd (S1 V12),
  4f1cdcf (S2 APIs), 22eb8ac (S3 providers), a117ccc (S4 factory),
  0c97e62 (S5 worker), plus this S6 close. Not pushed — bundle handoff
  per the working agreement. PostgreSQL validation of V12 rides CI on
  the PR (the migration-profile suite proves V1–V12 on H2-PG mode
  locally). Remaining M3 work: the frontend half (generate dialog, job
  polling, draft preview) and the Gemini quality gate.
- Follow-up: GET /api/v1/master/grade-subjects/{id}/topics added for
  the Generate dialog's topic picker (staff roles, name-ordered
  {topicId, name} list, unknown pairing → LRN-SYS-404); AdminFlowTest
  29 green, new code at 0 missed lines/branches.
- Follow-up: POST /api/v1/master/grade-subjects/{id}/topics added —
  create-or-return so teachers can name a topic the catalogue lacks
  (staff roles; name trimmed, case-insensitive match returns the
  existing topic with 200 and its original casing, a new name creates
  with 201; unknown pairing → LRN-SYS-404, validation → LRN-VAL-001).
  The insert runs in TopicCreationService's own transaction with a
  flush, and a lost race against uq_topics_name_per_grade_subject
  recovers by returning the winner — the material-dedupe split.
  Tests: AdminFlowTest 38 green (9 new, incl. a two-thread concurrent
  create ending in exactly one row) + CatalogServiceTest 3 green
  pinning the conflict branches deterministically; new code at 0
  missed lines/branches.

# Progress — feature/m3-generation-fixes (backend)

- Tracker task 80 (from Nagendra's 2026-10-29 local validation on
  Ollama), branched off feature/ai-generation (c475058). Three small
  commits, one per item; not pushed — bundle handoff per the working
  agreement.
- Done: prompt grade label — PromptBuilder no longer hardcodes a
  "Grade " prefix (grade display names already carry it), so the
  opening line reads "for Grade 6 Mathematics". Test pins the exact
  opening line for a grade named "Grade 6". Commit dc2988f;
  PromptBuilderTest 5 green.
- Done: ZERO_VALID retries within the existing 2-attempt budget — the
  worker reschedules a zero-valid response through the same claim
  service path as the transient failure kinds (PENDING + existing
  backoff); the concluding attempt records counts/usage, and only the
  final attempt lands FAILED/ZERO_VALID. Worker tests prove all three
  shapes: reschedule-then-terminal (counts + usage intact),
  zero-valid-then-success with drafts committed exactly once.
  Commit 1b1834f; GenerationWorkerTest 17 + ClaimService 6 + unit 1
  green.
- Done: FILL_BLANK presentation normalization in DraftQuestionFactory
  — stemWithBlank absent/blank falls back to the item's stem field,
  and runs of 5+ underscores collapse to the single _____ marker
  before the exactly-one check; the normalized stem is what the
  payload persists. Semantic rules unchanged (two real blanks, a
  4-underscore run, and a missing answer all still drop).
  Commit bb38166; DraftQuestionFactoryTest 30 green.
- Coverage: full generation package run (100 tests green) with a
  fresh JaCoCo report — PromptBuilder, GenerationWorker and
  DraftQuestionFactory all at 0 missed lines/branches/instructions.
  Full suite + gate remain the parent's from-scratch verification.
- Failing tests: none.
