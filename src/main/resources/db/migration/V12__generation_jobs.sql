-- V12: AI generation jobs (M3 branch 2) and question provenance.
--
-- BACKGROUND_JOB is the async work queue behind question generation: a
-- teacher's generate request freezes its inputs (material + content hash,
-- topic, per-type counts, difficulty, provider/model, prompt and payload
-- schema versions) so later configuration changes cannot alter a queued
-- job's meaning. Workers claim jobs with a token + generation pair and a
-- lease; every completion is a conditional update on that pair, so a
-- stale worker whose lease was reclaimed commits nothing.
--
-- Tenancy follows the V5/V6 discipline: the material link is a composite
-- (centre_id, material_id) foreign key, and questions reference the job
-- through a composite (centre_id, generation_job_id) key backed by the
-- redundant unique pair on background_job.

CREATE TABLE background_job (
    id                     uuid PRIMARY KEY,
    centre_id              uuid NOT NULL REFERENCES centres (centre_id),
    material_id            uuid NOT NULL,
    content_hash           varchar(64) NOT NULL,
    topic_id               uuid NOT NULL REFERENCES topics (topic_id),
    requested_counts       jsonb NOT NULL,
    difficulty             varchar(16) NOT NULL,
    provider               varchar(16) NOT NULL,
    model                  varchar(64) NOT NULL,
    prompt_version         varchar(32) NOT NULL,
    payload_schema_version varchar(32) NOT NULL,
    status                 varchar(16) NOT NULL,
    failure_kind           varchar(32),
    attempts               int NOT NULL DEFAULT 0,
    claim_token            uuid,
    claim_generation       int NOT NULL DEFAULT 0,
    lease_expires_at       timestamp with time zone,
    next_attempt_at        timestamp with time zone NOT NULL DEFAULT now(),
    result_counts          jsonb,
    usage_input_tokens     bigint,
    usage_output_tokens    bigint,
    usage_thinking_tokens  bigint,
    last_error_class       varchar(128),
    finished_at            timestamp with time zone,
    created_by             uuid,
    created_at             timestamp with time zone NOT NULL,
    updated_by             uuid,
    updated_at             timestamp with time zone NOT NULL,
    CONSTRAINT uq_background_job_centre_id UNIQUE (centre_id, id),
    CONSTRAINT fk_background_job_material
        FOREIGN KEY (centre_id, material_id) REFERENCES materials (centre_id, material_id)
);

-- The worker's claim scan: due jobs by status and schedule.
CREATE INDEX idx_background_job_due ON background_job (status, next_attempt_at);
CREATE INDEX idx_background_job_material ON background_job (material_id);

-- Question provenance: which job/provider/model produced a generated
-- question, under which prompt + payload schema, from which source text
-- (content hash of the complete extracted text, mirroring materials).
ALTER TABLE questions ADD COLUMN generation_job_id uuid;
ALTER TABLE questions ADD COLUMN generation_provider varchar(16);
ALTER TABLE questions ADD COLUMN generated_by_model varchar(64);
ALTER TABLE questions ADD COLUMN prompt_version varchar(32);
ALTER TABLE questions ADD COLUMN payload_schema_version varchar(32);
ALTER TABLE questions ADD COLUMN content_hash varchar(64);

ALTER TABLE questions
    ADD CONSTRAINT fk_questions_generation_job
        FOREIGN KEY (centre_id, generation_job_id)
        REFERENCES background_job (centre_id, id);
