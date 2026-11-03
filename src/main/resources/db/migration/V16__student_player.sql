-- M5 Part 2: attempts are tied to the frozen published item, not just the live bank question.
ALTER TABLE exam_attempts ADD COLUMN deadline_at timestamp with time zone;
UPDATE exam_attempts SET status = 'IN_PROGRESS' WHERE status = 'STARTED';
UPDATE exam_attempts SET deadline_at =
    (SELECT i.closes_at FROM exam_instances i WHERE i.exam_instance_id = exam_attempts.exam_instance_id)
    WHERE deadline_at IS NULL AND status = 'IN_PROGRESS';
ALTER TABLE exam_attempts ADD CONSTRAINT ck_exam_attempt_status
    CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'AUTO_SUBMITTED', 'INVALIDATED'));

ALTER TABLE exam_instance_questions ADD CONSTRAINT uq_eiq_centre_item
    UNIQUE (centre_id, exam_instance_question_id);
ALTER TABLE question_attempts ADD COLUMN exam_instance_question_id uuid;
UPDATE question_attempts qa SET exam_instance_question_id = (
    SELECT iq.exam_instance_question_id
    FROM exam_instance_questions iq
    JOIN exam_attempts a ON a.exam_instance_id = iq.exam_instance_id
    WHERE a.attempt_id = qa.attempt_id
      AND iq.question_id = qa.question_id
      AND iq.question_version = qa.question_version
);
ALTER TABLE question_attempts ALTER COLUMN exam_instance_question_id SET NOT NULL;
ALTER TABLE question_attempts DROP CONSTRAINT uq_question_attempts_attempt_question;
ALTER TABLE question_attempts ADD CONSTRAINT uq_question_attempts_attempt_item
    UNIQUE (attempt_id, exam_instance_question_id);
ALTER TABLE question_attempts ADD CONSTRAINT fk_question_attempts_centre_item
    FOREIGN KEY (centre_id, exam_instance_question_id)
    REFERENCES exam_instance_questions (centre_id, exam_instance_question_id);
CREATE INDEX idx_question_attempts_item ON question_attempts (exam_instance_question_id);
