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
