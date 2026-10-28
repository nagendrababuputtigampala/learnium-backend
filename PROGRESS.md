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
