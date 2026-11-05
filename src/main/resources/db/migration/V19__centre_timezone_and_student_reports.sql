-- M6: centre timezone for unambiguous report/display times (instants stay
-- timestamptz), and the STUDENT_REPORT snapshot table. A report is a frozen
-- artefact: its facts JSON is computed from attempt facts at generation time
-- and never changes afterwards; regeneration writes a new snapshot row.
ALTER TABLE centres ADD COLUMN timezone varchar(64) NOT NULL DEFAULT 'America/Vancouver';

CREATE TABLE student_reports (
    report_id       uuid PRIMARY KEY,
    centre_id       uuid NOT NULL,
    user_id         uuid NOT NULL,
    batch_id        uuid,
    facts           jsonb NOT NULL,
    teacher_comment varchar(2000),
    generated_by    uuid NOT NULL,
    generated_at    timestamp with time zone NOT NULL,
    created_by      uuid,
    created_at      timestamp with time zone NOT NULL,
    updated_by      uuid,
    updated_at      timestamp with time zone NOT NULL,
    CONSTRAINT fk_student_reports_centre
        FOREIGN KEY (centre_id) REFERENCES centres (centre_id),
    CONSTRAINT fk_student_reports_student
        FOREIGN KEY (centre_id, user_id) REFERENCES user_account (centre_id, user_id),
    CONSTRAINT fk_student_reports_batch
        FOREIGN KEY (centre_id, batch_id) REFERENCES batches (centre_id, batch_id),
    CONSTRAINT fk_student_reports_generated_by
        FOREIGN KEY (centre_id, generated_by) REFERENCES user_account (centre_id, user_id)
);
CREATE INDEX idx_student_reports_student ON student_reports (centre_id, user_id, generated_at);
