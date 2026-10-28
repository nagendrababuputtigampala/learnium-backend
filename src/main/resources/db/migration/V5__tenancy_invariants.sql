-- V5: tenancy invariants (independent review findings 1–3).
--
-- Every centre-owned child table gains a denormalised centre_id, backfilled
-- from its owning parent chain and then made NOT NULL. Composite foreign keys
-- (centre_id, parent_fk) -> parent (centre_id, parent_pk) then make it
-- impossible to STORE a cross-centre link (e.g. a roster row joining centre
-- A's batch to centre B's student) — the invariant lives in the database, not
-- in caller discipline.
--
-- The original single-column foreign keys from V2–V4 are KEPT deliberately:
-- their generated names differ per database, so dropping them here would be
-- unverifiable guesswork; the composite keys are purely additive defence and
-- the redundancy is harmless.

-- 1) centre_id columns (nullable until backfilled).
ALTER TABLE student_profiles ADD COLUMN centre_id uuid;
ALTER TABLE batch_memberships ADD COLUMN centre_id uuid;
ALTER TABLE exam_template_questions ADD COLUMN centre_id uuid;
ALTER TABLE exam_instances ADD COLUMN centre_id uuid;
ALTER TABLE exam_instance_questions ADD COLUMN centre_id uuid;
ALTER TABLE exam_attempts ADD COLUMN centre_id uuid;
ALTER TABLE question_attempts ADD COLUMN centre_id uuid;
ALTER TABLE user_topic_stats ADD COLUMN centre_id uuid;
ALTER TABLE user_badges ADD COLUMN centre_id uuid;
ALTER TABLE user_xp_ledger ADD COLUMN centre_id uuid;

-- 2) Backfill from the owning parent, in dependency order.
UPDATE student_profiles sp
SET centre_id = (SELECT ua.centre_id FROM user_account ua WHERE ua.user_id = sp.user_id);
UPDATE batch_memberships bm
SET centre_id = (SELECT b.centre_id FROM batches b WHERE b.batch_id = bm.batch_id);
UPDATE exam_template_questions tq
SET centre_id =
        (SELECT t.centre_id FROM exam_templates t WHERE t.exam_template_id = tq.exam_template_id);
UPDATE exam_instances i
SET centre_id = (SELECT b.centre_id FROM batches b WHERE b.batch_id = i.batch_id);
UPDATE exam_instance_questions iq
SET centre_id =
        (SELECT i.centre_id FROM exam_instances i WHERE i.exam_instance_id = iq.exam_instance_id);
UPDATE exam_attempts a
SET centre_id =
        (SELECT i.centre_id FROM exam_instances i WHERE i.exam_instance_id = a.exam_instance_id);
UPDATE question_attempts qa
SET centre_id = (SELECT a.centre_id FROM exam_attempts a WHERE a.attempt_id = qa.attempt_id);
UPDATE user_topic_stats s
SET centre_id = (SELECT ua.centre_id FROM user_account ua WHERE ua.user_id = s.user_id);
UPDATE user_badges ub
SET centre_id = (SELECT ua.centre_id FROM user_account ua WHERE ua.user_id = ub.user_id);
UPDATE user_xp_ledger x
SET centre_id = (SELECT ua.centre_id FROM user_account ua WHERE ua.user_id = x.user_id);

-- 3) Ownership is mandatory from now on.
ALTER TABLE student_profiles ALTER COLUMN centre_id SET NOT NULL;
ALTER TABLE batch_memberships ALTER COLUMN centre_id SET NOT NULL;
ALTER TABLE exam_template_questions ALTER COLUMN centre_id SET NOT NULL;
ALTER TABLE exam_instances ALTER COLUMN centre_id SET NOT NULL;
ALTER TABLE exam_instance_questions ALTER COLUMN centre_id SET NOT NULL;
ALTER TABLE exam_attempts ALTER COLUMN centre_id SET NOT NULL;
ALTER TABLE question_attempts ALTER COLUMN centre_id SET NOT NULL;
ALTER TABLE user_topic_stats ALTER COLUMN centre_id SET NOT NULL;
ALTER TABLE user_badges ALTER COLUMN centre_id SET NOT NULL;
ALTER TABLE user_xp_ledger ALTER COLUMN centre_id SET NOT NULL;

-- 4) Parent unique keys on (centre_id, pk) — the targets of the composite FKs.
ALTER TABLE user_account
    ADD CONSTRAINT uq_user_account_centre_user UNIQUE (centre_id, user_id);
ALTER TABLE batches ADD CONSTRAINT uq_batches_centre_batch UNIQUE (centre_id, batch_id);
ALTER TABLE exam_templates
    ADD CONSTRAINT uq_exam_templates_centre_template UNIQUE (centre_id, exam_template_id);
ALTER TABLE exam_instances
    ADD CONSTRAINT uq_exam_instances_centre_instance UNIQUE (centre_id, exam_instance_id);
ALTER TABLE exam_attempts
    ADD CONSTRAINT uq_exam_attempts_centre_attempt UNIQUE (centre_id, attempt_id);
ALTER TABLE questions ADD CONSTRAINT uq_questions_centre_question UNIQUE (centre_id, question_id);

-- 5) Composite foreign keys: a child row's centre must match its parent's.
ALTER TABLE batch_memberships
    ADD CONSTRAINT fk_batch_memberships_centre_batch
        FOREIGN KEY (centre_id, batch_id) REFERENCES batches (centre_id, batch_id);
ALTER TABLE batch_memberships
    ADD CONSTRAINT fk_batch_memberships_centre_user
        FOREIGN KEY (centre_id, user_id) REFERENCES user_account (centre_id, user_id);
ALTER TABLE student_profiles
    ADD CONSTRAINT fk_student_profiles_centre_user
        FOREIGN KEY (centre_id, user_id) REFERENCES user_account (centre_id, user_id);
ALTER TABLE exam_template_questions
    ADD CONSTRAINT fk_etq_centre_template
        FOREIGN KEY (centre_id, exam_template_id)
        REFERENCES exam_templates (centre_id, exam_template_id);
ALTER TABLE exam_template_questions
    ADD CONSTRAINT fk_etq_centre_question
        FOREIGN KEY (centre_id, question_id) REFERENCES questions (centre_id, question_id);
ALTER TABLE exam_instances
    ADD CONSTRAINT fk_exam_instances_centre_template
        FOREIGN KEY (centre_id, exam_template_id)
        REFERENCES exam_templates (centre_id, exam_template_id);
ALTER TABLE exam_instances
    ADD CONSTRAINT fk_exam_instances_centre_batch
        FOREIGN KEY (centre_id, batch_id) REFERENCES batches (centre_id, batch_id);
ALTER TABLE exam_instances
    ADD CONSTRAINT fk_exam_instances_centre_publisher
        FOREIGN KEY (centre_id, published_by) REFERENCES user_account (centre_id, user_id);
ALTER TABLE exam_instance_questions
    ADD CONSTRAINT fk_eiq_centre_instance
        FOREIGN KEY (centre_id, exam_instance_id)
        REFERENCES exam_instances (centre_id, exam_instance_id);
ALTER TABLE exam_instance_questions
    ADD CONSTRAINT fk_eiq_centre_question
        FOREIGN KEY (centre_id, question_id) REFERENCES questions (centre_id, question_id);
ALTER TABLE exam_attempts
    ADD CONSTRAINT fk_exam_attempts_centre_instance
        FOREIGN KEY (centre_id, exam_instance_id)
        REFERENCES exam_instances (centre_id, exam_instance_id);
ALTER TABLE exam_attempts
    ADD CONSTRAINT fk_exam_attempts_centre_user
        FOREIGN KEY (centre_id, user_id) REFERENCES user_account (centre_id, user_id);
ALTER TABLE question_attempts
    ADD CONSTRAINT fk_question_attempts_centre_attempt
        FOREIGN KEY (centre_id, attempt_id) REFERENCES exam_attempts (centre_id, attempt_id);
ALTER TABLE question_attempts
    ADD CONSTRAINT fk_question_attempts_centre_question
        FOREIGN KEY (centre_id, question_id) REFERENCES questions (centre_id, question_id);
ALTER TABLE user_topic_stats
    ADD CONSTRAINT fk_user_topic_stats_centre_user
        FOREIGN KEY (centre_id, user_id) REFERENCES user_account (centre_id, user_id);
ALTER TABLE user_badges
    ADD CONSTRAINT fk_user_badges_centre_user
        FOREIGN KEY (centre_id, user_id) REFERENCES user_account (centre_id, user_id);
ALTER TABLE user_xp_ledger
    ADD CONSTRAINT fk_user_xp_ledger_centre_user
        FOREIGN KEY (centre_id, user_id) REFERENCES user_account (centre_id, user_id);

-- 6) Template uniqueness is per centre (finding 3): two centres may both own a
-- "Fractions Check v1"; one centre may not own it twice.
-- Known caveat: PostgreSQL treats NULLs as distinct in UNIQUE constraints, so
-- rows with a NULL topic_id can still repeat within a centre. The service
-- layer guards that case when templates are created (M4).
ALTER TABLE exam_templates DROP CONSTRAINT uq_exam_templates_unique;
ALTER TABLE exam_templates
    ADD CONSTRAINT uq_exam_templates_unique
        UNIQUE (centre_id, grade_id, subject_id, topic_id, exam_mode_id, template_name, version);

-- 7) Exam modes are master data with exactly one mode per test type, which is
-- what ExamModeRepository.findByTestType already assumes.
ALTER TABLE exam_modes DROP CONSTRAINT uq_exam_modes_type_title;
ALTER TABLE exam_modes ADD CONSTRAINT uq_exam_modes_type UNIQUE (test_type);
