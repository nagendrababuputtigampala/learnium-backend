# M4 branch 2 — definitions, publishing and marking

All HTTP endpoints use `/api/v1`, staff authentication, the JWT centre and the standard
error envelope. Teachers own their definitions; centre admins manage every definition
in their centre. Instance management follows **current** batch assignments, including
instances published by another staff member. Invisible resources return 404.

## Definitions and frozen instances

| Method | Path | Response |
|---|---|---|
| POST | `/tests` | Create a definition, 201. |
| GET | `/tests` | Own definitions (admin: all centre definitions), summary list. |
| GET | `/tests/{id}` | Definition with ordered bank items. |
| PUT | `/tests/{id}` | Replace the definition name/pairing/kind and complete item list. |
| DELETE | `/tests/{id}` | Delete a never-published definition, 204. |
| POST | `/tests/{id}/publish` | Atomically freeze content/settings/active roster, 201. |
| GET | `/instances?batchId=...` | Manageable instances; batch filter is optional. |
| GET | `/instances/{id}` | Frozen items and assigned students. |
| POST | `/instances/{id}/regenerate-code` | Replace the eight-character join code. |
| POST | `/instances/{id}/close` | Idempotent early close with UTC `closedAt`. |
| POST | `/instances/{id}/students` | Add an active batch roster student; idempotent 200. |

Create/replace body:

```json
{
  "name": "Fractions check",
  "gradeSubjectId": "<pairing-id>",
  "kind": "SCORED_TEST",
  "items": [{"questionId": "<approved-question-id>", "marks": 2.5}]
}
```

A definition contains 1–200 distinct, active, approved questions from its centre and
one grade/subject pairing. Topics can differ. `SCORED_TEST` accepts MCQ, fill-blank,
numeric and written questions; marks must be positive, at most 9999.99 and have at most
two decimal places. `FLASHCARD_PRACTICE` accepts flashcards only, with zero marks (an
omitted mark becomes zero). Item array order is the display order.

Publish body:

```json
{
  "batchId": "<managed-batch-id>",
  "closesAt": "2026-11-02T23:00:00Z",
  "timeLimitSeconds": 1200,
  "maxAttempts": 1,
  "answerReleasePolicy": "AFTER_CLOSE",
  "integrityEnabled": false,
  "cameraMode": "OFF"
}
```

`opensAt` defaults to the current UTC instant. `closesAt` is required and must be after
opening; a supplied time limit must be positive. Defaults are one attempt, `AFTER_CLOSE`,
integrity off and camera `OFF`. Other release values are `ON_SUBMIT` and `NEVER`; camera
also accepts `ON`. The active batch must match the definition pairing and have at least
one ACTIVE student membership whose account is ACTIVE.

An optional `itemMarks` map overrides marks by selected question ID. For final review,
an optional **complete `items` array** selects/reorders/adds/removes questions for this
publish only; its marks follow the same validation as a definition. These extensions
never edit the saved definition. Questions are revalidated against the approved bank
before any instance is inserted.

One transaction inserts the instance, full payload and correct-reference snapshots,
decimal marks/order and the active student set. Definition name and kind are frozen too.
Later definition/bank/roster edits do not change an existing content or membership snapshot.
Only the explicit late-add endpoint grows its student set. A student add requires
`{"userId":"<active-roster-user-id>"}` and is blocked after early close or window expiry.

Instance responses derive `SCHEDULED`, `OPEN` or `CLOSED` from the window and `closedAt`.
The old join code disappears immediately on regeneration. `maxAttempts` is stored;
counted-attempt/report policy and INVALIDATED handling remain outside M4. Camera and
integrity are settings flags only.

| Code | HTTP | Meaning |
|---|---|---|
| LRN-TST-001 | 400 | Invalid definition, selected question, kind, marks or override; includes field errors. |
| LRN-TST-002 | 409 | Definition has published instances and cannot be deleted. |
| LRN-TST-003 | 400 | Invalid publish setting, empty roster, closed-instance add or invalid late joiner. |
| LRN-TST-004 | 404 | Missing or invisible definition, batch or instance. |

V14 reuses the ported `created_by`, `display_order`/`question_order`,
`integrity_signals_on` and `camera_mode` columns. It adds the missing decimal marks,
freezes, roster table and typed marking inputs. Legacy content/order/weights are
backfilled; flashcard practice becomes unscored. Missing legacy windows become historical
closed sittings. V1–V13 retain their published checksums.

## Internal marking entry point

`MarkingJobService.enqueueMarking(questionAttemptId)` is internal and called only from
tests in M4. M5 will supply authenticated submission orchestration. A fabricated answer
uses `answer_payload: {"text":"..."}` and references a written question/version in its
published instance. The job freezes the instance's written payload, criterion IDs,
student text, provider, model and `marking-prompt-v1`; retries cannot read changed inputs.

`MARKING_PROVIDER` and `MARKING_MODEL` independently default to the generation provider
and active model when blank. Explicit values are frozen onto the job. Both Ollama and
Gemini use the existing `ModelTransport` seam and structured output:

```json
{
  "criteria": [
    {"criterionId": "crit-1", "score": 0.75, "feedback": "Identifies equal parts"}
  ],
  "feedback": "Overall feedback"
}
```

The example abbreviates the criterion list: every frozen criterion must appear exactly
once. Unknown/duplicate/missing IDs, non-finite/negative/over-limit scores, more than two
score decimal places or malformed feedback fail terminally without partial writes.
Transport/network, timeout, 429/5xx and truncation failures can retry, within two total
claims. The model call holds no transaction. Persistence checks the RUNNING claim token
and generation in the same transaction as answer updates; stale claims cannot write.
Expired second leases fail without making a third model call.

Success stores `ai_breakdown.criteria` keyed by criterion ID, overall feedback and prompt
version, plus the raw criterion-score sum in `ai_suggested_score`. Status stays `PENDING`;
confirmed scores remain untouched. An already confirmed answer cannot be overwritten by
a late worker. No student answer text, prompt or model response is logged. Generation
claim scans and quotas exclude MARKING jobs. The existing generation worker-enabled flag
also controls the marking scheduler; tests drive both workers explicitly.

The student player, submission trigger, teacher marking queue, confirmation, reports and
camera/integrity event capture are outside this branch.
