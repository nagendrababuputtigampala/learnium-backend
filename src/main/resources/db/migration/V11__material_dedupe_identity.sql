-- V11: database-enforced dedupe identity for materials (M3 branch 1,
-- Codex fix round F-3).
--
-- V10's (centre_id, uploaded_by, content_hash) index is a lookup index,
-- not a uniqueness guarantee: two simultaneous uploads of identical
-- content could both pass the service's dedupe lookup and both insert,
-- after which the lookup itself broke (IncorrectResultSizeDataAccess-
-- Exception — it expects at most one match). The application now
-- maintains a dedupe_hash column: it mirrors content_hash while the
-- material is ACTIVE and is cleared on soft delete, so a plain unique
-- index over (centre_id, uploaded_by, dedupe_hash) makes the identity
-- unique for exactly the rows dedupe may match. Rows whose identity is
-- NULL (failed or pre-V10 extractions, deleted rows) never collide —
-- NULLs are distinct in a unique index on PostgreSQL and H2 alike. (A
-- partial unique index would express this directly but is not portable
-- to H2, so the identity is a maintained column instead — the pattern
-- V8 set for global email uniqueness.)

ALTER TABLE materials ADD COLUMN dedupe_hash varchar(64);

-- Backfill active rows, keeping the earliest row per identity; later
-- duplicates stay visible as separate materials with a NULL identity
-- rather than failing this migration.
UPDATE materials m
SET dedupe_hash = m.content_hash
WHERE m.content_hash IS NOT NULL
  AND m.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM materials o
      WHERE o.centre_id = m.centre_id
        AND o.uploaded_by = m.uploaded_by
        AND o.content_hash = m.content_hash
        AND o.deleted_at IS NULL
        AND o.material_id < m.material_id);

CREATE UNIQUE INDEX uq_materials_dedupe_identity
    ON materials (centre_id, uploaded_by, dedupe_hash);
