-- M5 Part 5: exam-integrity events and camera proctor snapshots.
-- Integrity events flag behaviour only; they never change an attempt's outcome.
CREATE TABLE integrity_events (
    event_id    uuid PRIMARY KEY,
    centre_id   uuid NOT NULL,
    attempt_id  uuid NOT NULL,
    event_type  varchar(30) NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    created_by  uuid,
    created_at  timestamp with time zone NOT NULL,
    updated_by  uuid,
    updated_at  timestamp with time zone NOT NULL,
    CONSTRAINT fk_integrity_events_centre_attempt
        FOREIGN KEY (centre_id, attempt_id) REFERENCES exam_attempts (centre_id, attempt_id),
    CONSTRAINT ck_integrity_events_type
        CHECK (event_type IN ('TAB_HIDDEN', 'FOCUS_LOST', 'MOUSE_LEFT', 'FULLSCREEN_EXIT', 'CAMERA_LOST'))
);
CREATE INDEX idx_integrity_events_attempt ON integrity_events (attempt_id, occurred_at);
CREATE INDEX idx_integrity_events_centre ON integrity_events (centre_id);

-- Snapshots are still images only (never video), auto-deleted after 30 days.
-- attempt_id is nullable because the PRE_TEST camera check happens before an
-- attempt exists: the row is created against (instance, student) and linked to
-- the attempt when the check token is consumed at start.
CREATE TABLE proctor_snapshots (
    snapshot_id      uuid PRIMARY KEY,
    centre_id        uuid NOT NULL,
    attempt_id       uuid,
    exam_instance_id uuid NOT NULL,
    user_id          uuid NOT NULL,
    storage_key      varchar(500) NOT NULL,
    reason           varchar(20) NOT NULL,
    captured_at      timestamp with time zone NOT NULL,
    check_token_hash varchar(64),
    check_expires_at timestamp with time zone,
    check_used_at    timestamp with time zone,
    created_by       uuid,
    created_at       timestamp with time zone NOT NULL,
    updated_by       uuid,
    updated_at       timestamp with time zone NOT NULL,
    CONSTRAINT fk_proctor_snapshots_centre_attempt
        FOREIGN KEY (centre_id, attempt_id) REFERENCES exam_attempts (centre_id, attempt_id),
    CONSTRAINT fk_proctor_snapshots_instance
        FOREIGN KEY (exam_instance_id) REFERENCES exam_instances (exam_instance_id),
    CONSTRAINT fk_proctor_snapshots_user
        FOREIGN KEY (user_id) REFERENCES user_account (user_id),
    CONSTRAINT ck_proctor_snapshots_reason
        CHECK (reason IN ('PRE_TEST', 'PERIODIC', 'EVENT'))
);
CREATE INDEX idx_proctor_snapshots_attempt ON proctor_snapshots (attempt_id, captured_at);
CREATE INDEX idx_proctor_snapshots_captured ON proctor_snapshots (captured_at);
CREATE INDEX idx_proctor_snapshots_check_token ON proctor_snapshots (check_token_hash);
