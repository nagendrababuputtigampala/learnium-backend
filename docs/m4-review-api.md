# M4 branch 1 — review and bank API

All endpoints are under `/api/v1/questions`, require staff authentication, and use the
centre from the authenticated token. Drafts/rejections are creator/admin-only; approved
questions are shared with all centre staff. Student requests are rejected.

| Method | Path | Behavior |
|---|---|---|
| GET | `/questions?status=DRAFT` | Paged summary-only review queue; `APPROVED` selects the bank and `REJECTED` the private archive. |
| GET | `/questions/{id}` | Visible detail, including staff-only payload/answer key, revision, topic/difficulty and approval/provenance. |
| PUT | `/questions/{id}` | Validate and save a draft; increment content version and concurrency revision. |
| POST | `/questions/{id}/approve` | Revalidate saved content, stamp actor/time and atomically approve. |
| POST | `/questions/{id}/reject` | Reject a draft, including structurally invalid content. |
| POST | `/questions/{id}/forks` | Copy an approved question into a new draft owned by the caller; returns 201. |

List responses contain a bounded `stemPreview` plus type, topic, difficulty, status,
material, creator and approval metadata. Payloads and answer keys are returned only by detail
and mutation endpoints. Projections use joins, without serializing entities or loading PDF text.

List filters: `materialId`, `gradeSubjectId`, `gradeId`, `subjectId`, `topicId`, `type`,
`difficulty` (1–5), `status`, `page`, `size` (capped at 100). Pagination is stable by
creation time and question ID. The UI groups each page by material ID, with a separate
group for forks without source material. Scalar projections do not load/return PDF text.

## Save a draft

```json
{
  "revision": 0,
  "topicId": "<topic-uuid>",
  "difficulty": 3,
  "content": {
    "stem": "Which fraction is one half?",
    "options": [{"text":"1/2"},{"text":"1/3"},{"text":"1/4"},{"text":"1/5"}],
    "correctIndex": 0
  }
}
```

`content` uses the M3 raw per-type field names. Type and grade/subject are server-owned; changing `type`, `content.type`, or
`gradeSubjectId` returns 400 `LRN-VAL-001`. A topic outside the original pairing,
an inactive topic or a missing topic also returns 400. The stored payload is normalized by the shared factory. Written
criteria retain their server IDs across edits/reordering; omit an ID for a new criterion.
Unknown/duplicate IDs are rejected. Written payloads include `maxScore`, equal to the
sum of criterion scores and at most 100. Legacy generation payloads without a declared
total derive it from the rubric through the shared validator. New criteria receive distinct IDs within the saved draft.

Approve/reject bodies are `{"revision": <latest-revision>}`. Approval always validates the
saved version; the UI disables approval while there are unsaved changes. `revision` is
the optimistic concurrency token, separate from the content version. Saves and decisions
increment it; transactional row locking serializes concurrent writers.

V13 was already merged in backend PR #12 before this follow-up. Its published schema
(including `row_revision` and review indexes) and checksum are preserved for existing databases;
this follow-up adds no schema changes.

## Errors

The standard envelope includes `code`, `message`, `traceId`, `timestamp`, and optional
`fieldErrors`. Question validation reports paths such as `content.stem`, `content.options`,
`content.correctIndex`, `content.rubric` or `topicId`, without echoing submitted content.

| Code | HTTP | Meaning |
|---|---|---|
| LRN-QST-001 | 409 | Non-draft mutation or fork of a non-approved question. |
| LRN-QST-002 | 404 | Missing, inactive, foreign-centre or otherwise invisible question. |
| LRN-QST-003 | 422 | Per-type content, topic or criterion identity validation failed. |
| LRN-QST-004 | 409 | Stale revision; reload before retrying. |

Malformed UUID parameters return 400. Missing DTO fields, non-object content and out-of-range difficulty
return the standard 400 decoding/Bean Validation envelope. No endpoint edits an approved question,
reopens a rejection, creates questions from scratch, or exposes student answer APIs.

## Validation

```sh
mvn -B verify
# Against a disposable PostgreSQL database, using the same API tests:
LEARINUM_PG_TEST=true LEARINUM_PG_URL=jdbc:postgresql://localhost:5432/learnium \
LEARINUM_PG_USERNAME=learnium LEARINUM_PG_PASSWORD=learnium \
mvn -B -Dtest=PostgresMigrationTest,PostgresQuestionReviewTest test
```

The real PostgreSQL API suite includes concurrent edits and validates nullable filter
bindings. CI runs it in the existing isolated PostgreSQL service.
