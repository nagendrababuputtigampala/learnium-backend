# M4 — settled requirements

Source: `M4 Teacher User Flow.html` (complete specification), with all ten recommendations
accepted by Nagendra on 2026-11-02. This supersedes conflicting older tracker wording.

## Delivery boundaries

1. `feature/review-and-bank`, based on merged dev: backend + frontend review, per-type edits,
   approve/reject, approval metadata, shared bank, immutable approved questions and draft forks;
   Flyway V13. No question generation is added to M4.
2. `feature/publish`, following branch 1: reusable definitions, final-review overrides,
   publish settings, frozen items/roster, live-instance controls and written marking engine;
   Flyway V14. Submission triggers, student players and the teacher marking queue remain M5.

## Settled policies

- Teachers manage their own definitions; centre admins manage all centre definitions.
  Current batch assignments govern teacher access to published instances; admins have centre-wide access.
- Draft/rejected questions: creator + centre admin only. Approved bank: all centre staff.
  Sharing a question does not grant access to its source material or PDF.
- Only drafts can be edited. Topic may change within the original grade/subject; difficulty
  may change. Question type and grade/subject remain fixed. Invalid drafts may be rejected
  immediately; saving and approval use the shared M3 validators.
- Approval stamps actor/time and makes content immutable. Changes fork a new creator-owned
  draft with lineage; the original remains untouched. Forks must pass review again.
- A definition belongs to one grade/subject and may span topics. It is either a scored test
  (MCQ/fill-blank/numeric/written) or an unscored flashcard practice set; these are never mixed.
- Marks support two decimal places. Written rubric scores act as weights; validated totals
  scale proportionally to the frozen published question marks. No malformed AI breakdown is partially written.
- Maximum attempts defaults to 1. With retakes, the **highest confirmed score** counts and
  invalidated attempts are excluded. This deliberately supersedes task 69's earlier
  “latest valid attempt counts” rule. Unconfirmed written scores do not enter totals.
- Answer-release choices: after closing (default), after submission, or never. Written
  marks stay pending until teacher confirmation.
- Opening defaults to now; closing is required; time limit is optional. Store UTC and label
  the publish form with its explicit local timezone. Per-centre timezone settings remain
  M6/task 71. M5 ends an attempt at the earlier of its time limit and closing time.
- Early close prevents new attempts and ends active attempts using saved answers in M5.
- Publishing requires a non-empty active student roster and snapshots it atomically with
  full payloads, answer keys, rubrics, marks/order and join code. Later definition edits
  never alter existing instances. A definition may be deleted only if never published.
- Late joiners must be active roster members and added explicitly per open test. Closed
  tests reject additions. Joining a batch never assigns earlier tests automatically.
- Marking has separate provider/model settings that inherit generation defaults, frozen per
  MARKING job. Use the existing transport seam and ownership/lease/retry discipline.
  M4 proves the engine using fabricated attempts; it does not enqueue from submissions
  and has no marking queue UI. M5 supplies both.

## Branch 1 acceptance

- Scalar paged projections; server-side centre/creator visibility for list and detail.
- Per-type edit forms and field errors from the same validators as generation.
- Approval revalidates stored content; rejection requires no valid content.
- Revision checks and transactional row locking prevent stale/concurrent decisions.
- Forks preserve origin provenance and criterion IDs, have their own creator, clear
  approval metadata and do not expose the original private material.
- Filters: grade, subject/pairing, topic, type, difficulty, material and review status.
- Material previews link into the chapter's review queue and display question status.
- Full backend/frontend coverage gates and real PostgreSQL migration/API/concurrency tests.

The three-chapter educational quality gate (#20), final M2 UI acceptance (#72) and
deployment observability checks remain independent acceptance work.
