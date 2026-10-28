# Progress — feature/port-domain-model (backend)

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
- CODEX REVIEW FIXES COMPLETE (S1–S6). Remaining before this branch is
  re-bundled for Nagendra: final full `mvn verify` + coverage gate.
- Failing tests: none.
