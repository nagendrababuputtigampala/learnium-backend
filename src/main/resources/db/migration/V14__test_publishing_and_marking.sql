-- M4 branch 2: extend the ported exam chain without duplicating its existing
-- creator/order/integrity/camera columns. Preserve all published migrations.
INSERT INTO exam_modes (exam_mode_id, test_type, title, created_at, updated_at)
    SELECT '50000000-0000-4000-8000-000000000001', 'quiz', 'Scored test', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    WHERE NOT EXISTS (SELECT 1 FROM exam_modes WHERE test_type = 'quiz');
INSERT INTO exam_modes (exam_mode_id, test_type, title, created_at, updated_at)
    SELECT '50000000-0000-4000-8000-000000000002', 'flashcards', 'Flashcard practice', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    WHERE NOT EXISTS (SELECT 1 FROM exam_modes WHERE test_type = 'flashcards');
ALTER TABLE exam_templates ADD COLUMN kind varchar(24) NOT NULL DEFAULT 'SCORED_TEST';
UPDATE exam_templates t SET kind = 'FLASHCARD_PRACTICE' WHERE EXISTS (SELECT 1 FROM exam_modes m WHERE m.exam_mode_id = t.exam_mode_id AND m.test_type = 'flashcards');
ALTER TABLE exam_templates ADD COLUMN grade_subject_id uuid REFERENCES grade_subjects (grade_subject_id);
UPDATE exam_templates t SET grade_subject_id =
    (SELECT gs.grade_subject_id FROM grade_subjects gs WHERE gs.grade_id = t.grade_id AND gs.subject_id = t.subject_id);
ALTER TABLE exam_templates ALTER COLUMN grade_subject_id SET NOT NULL;
ALTER TABLE exam_template_questions ADD COLUMN marks numeric(6,2) NOT NULL DEFAULT 1;
UPDATE exam_template_questions SET marks = points;
UPDATE exam_template_questions tq SET marks = 0 WHERE EXISTS (SELECT 1 FROM exam_templates t WHERE t.exam_template_id = tq.exam_template_id AND t.kind = 'FLASHCARD_PRACTICE');
-- Freeze definition identity too: later definition name/kind edits cannot relabel a sitting.
ALTER TABLE exam_instances ADD COLUMN definition_name varchar(120) NOT NULL DEFAULT '';
ALTER TABLE exam_instances ADD COLUMN definition_kind varchar(24) NOT NULL DEFAULT 'SCORED_TEST';
UPDATE exam_instances i SET definition_name = (SELECT t.template_name FROM exam_templates t WHERE t.exam_template_id = i.exam_template_id),
    definition_kind = (SELECT t.kind FROM exam_templates t WHERE t.exam_template_id = i.exam_template_id);
ALTER TABLE exam_instances ADD COLUMN time_limit_seconds integer;
ALTER TABLE exam_instances ADD COLUMN max_attempts integer NOT NULL DEFAULT 1;
UPDATE exam_instances SET max_attempts = 2 WHERE retakes_allowed = true;
ALTER TABLE exam_instances ADD COLUMN answer_release_policy varchar(16) NOT NULL DEFAULT 'AFTER_CLOSE';
ALTER TABLE exam_instances ADD COLUMN closed_at timestamp with time zone;
-- Legacy rows without a complete window are historical closed sittings.
UPDATE exam_instances SET opens_at = created_at WHERE opens_at IS NULL;
UPDATE exam_instances SET closes_at = created_at WHERE closes_at IS NULL;
-- integrity_signals_on and camera_mode already exist; question_order is display_order.
UPDATE exam_instances SET camera_mode = 'ON' WHERE camera_mode = 'VERIFY_SNAPSHOTS';
ALTER TABLE exam_instance_questions ADD COLUMN payload_snapshot jsonb;
ALTER TABLE exam_instance_questions ADD COLUMN correct_ref_snapshot varchar(50);
ALTER TABLE exam_instance_questions ADD COLUMN marks numeric(6,2) NOT NULL DEFAULT 1;
UPDATE exam_instance_questions iq SET payload_snapshot = (SELECT q.payload FROM questions q WHERE q.question_id = iq.question_id),
    correct_ref_snapshot = (SELECT q.correct_ref FROM questions q WHERE q.question_id = iq.question_id), marks = points;
UPDATE exam_instance_questions iq SET marks = 0 WHERE EXISTS (SELECT 1 FROM exam_instances i WHERE i.exam_instance_id = iq.exam_instance_id AND i.definition_kind = 'FLASHCARD_PRACTICE');
ALTER TABLE exam_instance_questions ALTER COLUMN payload_snapshot SET NOT NULL;
ALTER TABLE exam_instance_questions ALTER COLUMN correct_ref_snapshot SET NOT NULL;
CREATE TABLE exam_instance_student (
    id uuid PRIMARY KEY,
    centre_id uuid NOT NULL REFERENCES centres (centre_id),
    exam_instance_id uuid NOT NULL,
    user_id uuid NOT NULL,
    added_by uuid NOT NULL,
    added_at timestamp with time zone NOT NULL,
    CONSTRAINT uq_instance_student UNIQUE (exam_instance_id, user_id),
    CONSTRAINT fk_instance_student_instance FOREIGN KEY (centre_id, exam_instance_id) REFERENCES exam_instances (centre_id, exam_instance_id),
    CONSTRAINT fk_instance_student_user FOREIGN KEY (centre_id, user_id) REFERENCES user_account (centre_id, user_id),
    CONSTRAINT fk_instance_student_actor FOREIGN KEY (centre_id, added_by) REFERENCES user_account (centre_id, user_id)
);
ALTER TABLE question_attempts ADD COLUMN ai_breakdown jsonb;
-- Marking shares the existing job ownership/lease protocol. Generation inputs
-- are optional only for MARKING; its frozen input contains the answer and rubric.
ALTER TABLE background_job ADD COLUMN job_type varchar(16) NOT NULL DEFAULT 'GENERATION';
ALTER TABLE background_job ADD COLUMN question_attempt_id uuid;
ALTER TABLE background_job ADD COLUMN marking_input jsonb;
ALTER TABLE background_job ALTER COLUMN material_id DROP NOT NULL;
ALTER TABLE background_job ALTER COLUMN content_hash DROP NOT NULL;
ALTER TABLE background_job ALTER COLUMN topic_id DROP NOT NULL;
ALTER TABLE background_job ALTER COLUMN requested_counts DROP NOT NULL;
ALTER TABLE background_job ALTER COLUMN difficulty DROP NOT NULL;
ALTER TABLE question_attempts ADD CONSTRAINT uq_question_attempt_centre_id UNIQUE (centre_id, question_attempt_id);
ALTER TABLE background_job ADD CONSTRAINT fk_marking_attempt FOREIGN KEY (centre_id, question_attempt_id)
    REFERENCES question_attempts (centre_id, question_attempt_id);
ALTER TABLE background_job ADD CONSTRAINT uq_marking_attempt UNIQUE (question_attempt_id);
ALTER TABLE background_job ADD CONSTRAINT ck_job_inputs CHECK (
    (job_type = 'GENERATION' AND material_id IS NOT NULL AND content_hash IS NOT NULL AND topic_id IS NOT NULL AND requested_counts IS NOT NULL AND difficulty IS NOT NULL AND question_attempt_id IS NULL)
    OR (job_type = 'MARKING' AND question_attempt_id IS NOT NULL AND marking_input IS NOT NULL));
CREATE INDEX idx_background_job_type_due ON background_job (job_type, status, next_attempt_at);
