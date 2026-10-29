-- V10: material extraction + storage metadata (M3 branch 1, pdf-extraction).
--
-- M1 created `materials` with title / source_type / storage_key /
-- extracted_text. M3 adds what the extraction pipeline records about each
-- upload: the SHA-256 of the extracted text (provenance for generation in
-- branch 2, and same-uploader deduplication), the extraction outcome
-- (OK / NO_TEXT / UNUSABLE_TEXT / OVER_LIMIT / EXTRACTION_FAILED), the
-- stored character count, and a soft-delete marker. Deletion is a marker,
-- not a row delete: a generation job may be reading the material while a
-- teacher deletes it, and the stored file is cleaned up later (deferred
-- cleanup) once nothing references the row.
--
-- extracted_text already exists (V3); it is not re-added here.

ALTER TABLE materials ADD COLUMN content_hash varchar(64);
ALTER TABLE materials ADD COLUMN extraction_status varchar(24);
ALTER TABLE materials ADD COLUMN extracted_chars integer;
ALTER TABLE materials ADD COLUMN deleted_at timestamp with time zone;

-- Dedupe + list lookups are always scoped by centre and uploader.
CREATE INDEX idx_materials_centre_uploader_hash
    ON materials (centre_id, uploaded_by, content_hash);
CREATE INDEX idx_materials_deleted_at ON materials (deleted_at);
