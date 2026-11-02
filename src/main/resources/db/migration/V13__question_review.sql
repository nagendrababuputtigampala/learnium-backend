ALTER TABLE questions ADD COLUMN approved_by uuid;
ALTER TABLE questions ADD COLUMN approved_at timestamp with time zone;
ALTER TABLE questions ADD COLUMN source_question_id uuid;
ALTER TABLE questions ADD CONSTRAINT fk_questions_approver_centre
    FOREIGN KEY (centre_id, approved_by) REFERENCES user_account (centre_id, user_id);
ALTER TABLE questions ADD CONSTRAINT fk_questions_source_centre
    FOREIGN KEY (centre_id, source_question_id) REFERENCES questions (centre_id, question_id);
-- The required (centre_id, status) index already exists from V3.
