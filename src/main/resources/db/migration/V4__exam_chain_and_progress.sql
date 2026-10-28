-- V4: exam chain (mode, template, published instance, attempts with theory
-- marking) plus progress/gamification tables (topic stats, badges, XP ledger).

CREATE TABLE exam_modes (
    exam_mode_id  uuid PRIMARY KEY,
    test_type     varchar(30) NOT NULL,
    title         varchar(150) NOT NULL,
    description   varchar(255),
    icon          varchar(255),
    button_text   varchar(255),
    button_color  varchar(255),
    gradient_from varchar(255),
    gradient_to   varchar(255),
    features      jsonb,
    sort_order    integer NOT NULL DEFAULT 0,
    is_active     boolean NOT NULL DEFAULT true,
    created_by    uuid,
    created_at    timestamp with time zone NOT NULL,
    updated_by    uuid,
    updated_at    timestamp with time zone NOT NULL,
    CONSTRAINT uq_exam_modes_type_title UNIQUE (test_type, title)
);
CREATE INDEX idx_exam_modes_active_sort ON exam_modes (is_active, sort_order);

CREATE TABLE exam_templates (
    exam_template_id uuid PRIMARY KEY,
    centre_id        uuid NOT NULL REFERENCES centres (centre_id),
    grade_id         uuid NOT NULL REFERENCES grades (grade_id),
    subject_id       uuid NOT NULL REFERENCES subjects (subject_id),
    topic_id         uuid REFERENCES topics (topic_id),
    exam_mode_id     uuid NOT NULL REFERENCES exam_modes (exam_mode_id),
    template_name    varchar(120) NOT NULL,
    description      varchar(255),
    rules            jsonb NOT NULL,
    version          integer NOT NULL DEFAULT 1,
    is_active        boolean NOT NULL DEFAULT true,
    created_by       uuid,
    created_at       timestamp with time zone NOT NULL,
    updated_by       uuid,
    updated_at       timestamp with time zone NOT NULL,
    CONSTRAINT uq_exam_templates_unique
        UNIQUE (grade_id, subject_id, topic_id, exam_mode_id, template_name, version)
);
CREATE INDEX idx_exam_templates_lookup
    ON exam_templates (grade_id, subject_id, topic_id, exam_mode_id, is_active, version);
CREATE INDEX idx_exam_templates_centre ON exam_templates (centre_id);

CREATE TABLE exam_template_questions (
    exam_template_question_id uuid PRIMARY KEY,
    exam_template_id          uuid NOT NULL REFERENCES exam_templates (exam_template_id),
    question_id               uuid NOT NULL REFERENCES questions (question_id),
    question_version          integer NOT NULL DEFAULT 1,
    display_order             integer NOT NULL,
    points                    smallint NOT NULL DEFAULT 1,
    section                   varchar(60),
    is_required               boolean NOT NULL DEFAULT true,
    is_active                 boolean NOT NULL DEFAULT true,
    created_by                uuid,
    created_at                timestamp with time zone NOT NULL,
    updated_by                uuid,
    updated_at                timestamp with time zone NOT NULL,
    CONSTRAINT uq_etq_template_question_version
        UNIQUE (exam_template_id, question_id, question_version),
    CONSTRAINT uq_etq_template_display_order
        UNIQUE (exam_template_id, display_order)
);
CREATE INDEX idx_etq_template_active_order
    ON exam_template_questions (exam_template_id, is_active, display_order);
CREATE INDEX idx_etq_question ON exam_template_questions (question_id);

CREATE TABLE exam_instances (
    exam_instance_id     uuid PRIMARY KEY,
    exam_template_id     uuid NOT NULL REFERENCES exam_templates (exam_template_id),
    batch_id             uuid NOT NULL REFERENCES batches (batch_id),
    published_by         uuid NOT NULL REFERENCES user_account (user_id),
    join_code            varchar(20) NOT NULL,
    opens_at             timestamp with time zone,
    closes_at            timestamp with time zone,
    status               varchar(20) NOT NULL DEFAULT 'PUBLISHED',
    integrity_signals_on boolean NOT NULL DEFAULT false,
    camera_mode          varchar(20) NOT NULL DEFAULT 'OFF',
    retakes_allowed      boolean NOT NULL DEFAULT false,
    created_by           uuid,
    created_at           timestamp with time zone NOT NULL,
    updated_by           uuid,
    updated_at           timestamp with time zone NOT NULL,
    CONSTRAINT uq_exam_instances_join_code UNIQUE (join_code)
);
CREATE INDEX idx_exam_instances_batch ON exam_instances (batch_id, status);
CREATE INDEX idx_exam_instances_template ON exam_instances (exam_template_id);

CREATE TABLE exam_instance_questions (
    exam_instance_question_id uuid PRIMARY KEY,
    exam_instance_id          uuid NOT NULL REFERENCES exam_instances (exam_instance_id),
    question_id               uuid NOT NULL REFERENCES questions (question_id),
    question_order            integer NOT NULL,
    question_version          integer NOT NULL DEFAULT 1,
    points                    smallint NOT NULL DEFAULT 1,
    created_by                uuid,
    created_at                timestamp with time zone NOT NULL,
    updated_by                uuid,
    updated_at                timestamp with time zone NOT NULL,
    CONSTRAINT uq_exam_instance_question UNIQUE (exam_instance_id, question_id),
    CONSTRAINT uq_exam_instance_order UNIQUE (exam_instance_id, question_order)
);
CREATE INDEX idx_exam_instance_questions_instance
    ON exam_instance_questions (exam_instance_id, question_order);

CREATE TABLE exam_attempts (
    attempt_id            uuid PRIMARY KEY,
    exam_instance_id      uuid NOT NULL REFERENCES exam_instances (exam_instance_id),
    user_id               uuid NOT NULL REFERENCES user_account (user_id),
    attempt_no            integer NOT NULL DEFAULT 1,
    status                varchar(20) NOT NULL DEFAULT 'STARTED',
    started_at            timestamp with time zone NOT NULL,
    submitted_at          timestamp with time zone,
    total_questions       integer NOT NULL DEFAULT 0,
    correct_count         integer NOT NULL DEFAULT 0,
    total_score           numeric(38, 2),
    integrity_flag_count  integer NOT NULL DEFAULT 0,
    created_by            uuid,
    created_at            timestamp with time zone NOT NULL,
    updated_by            uuid,
    updated_at            timestamp with time zone NOT NULL,
    CONSTRAINT uq_exam_attempts_instance_user_no UNIQUE (exam_instance_id, user_id, attempt_no)
);
CREATE INDEX idx_exam_attempts_instance ON exam_attempts (exam_instance_id);
CREATE INDEX idx_exam_attempts_user ON exam_attempts (user_id, submitted_at);

CREATE TABLE question_attempts (
    question_attempt_id uuid PRIMARY KEY,
    attempt_id          uuid NOT NULL REFERENCES exam_attempts (attempt_id),
    question_id         uuid NOT NULL REFERENCES questions (question_id),
    question_version    integer NOT NULL,
    answer_payload      jsonb NOT NULL,
    is_correct          boolean,
    ai_suggested_score  numeric(38, 2),
    confirmed_score     numeric(38, 2),
    marking_status      varchar(20) NOT NULL DEFAULT 'AUTO',
    marker_feedback     text,
    points              smallint NOT NULL DEFAULT 1,
    created_by          uuid,
    created_at          timestamp with time zone NOT NULL,
    updated_by          uuid,
    updated_at          timestamp with time zone NOT NULL,
    CONSTRAINT uq_question_attempts_attempt_question UNIQUE (attempt_id, question_id)
);
CREATE INDEX idx_question_attempts_attempt ON question_attempts (attempt_id);
CREATE INDEX idx_question_attempts_question ON question_attempts (question_id);

CREATE TABLE user_topic_stats (
    user_topic_stats_id uuid PRIMARY KEY,
    user_id             uuid NOT NULL REFERENCES user_account (user_id),
    topic_id            uuid NOT NULL REFERENCES topics (topic_id),
    attempts            integer NOT NULL DEFAULT 0,
    correct             integer NOT NULL DEFAULT 0,
    avg_time_ms         integer NOT NULL DEFAULT 0,
    xp_total            integer NOT NULL DEFAULT 0,
    last_activity_at    timestamp with time zone,
    created_by          uuid,
    created_at          timestamp with time zone NOT NULL,
    updated_by          uuid,
    updated_at          timestamp with time zone NOT NULL,
    CONSTRAINT uq_user_topic_stats UNIQUE (user_id, topic_id)
);
CREATE INDEX idx_user_topic_stats_user ON user_topic_stats (user_id);
CREATE INDEX idx_user_topic_stats_topic ON user_topic_stats (topic_id);

CREATE TABLE badges (
    badge_id   uuid PRIMARY KEY,
    code       varchar(80) NOT NULL,
    name       varchar(150) NOT NULL,
    description varchar(255),
    icon       varchar(255),
    color      varchar(255),
    rule       jsonb NOT NULL,
    is_active  boolean NOT NULL DEFAULT true,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    updated_by uuid,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT uq_badges_code UNIQUE (code)
);
CREATE INDEX idx_badges_active ON badges (is_active);

CREATE TABLE user_badges (
    user_badge_id uuid PRIMARY KEY,
    user_id       uuid NOT NULL REFERENCES user_account (user_id),
    badge_id      uuid NOT NULL REFERENCES badges (badge_id),
    awarded_at    timestamp with time zone NOT NULL,
    created_by    uuid,
    created_at    timestamp with time zone NOT NULL,
    updated_by    uuid,
    updated_at    timestamp with time zone NOT NULL,
    CONSTRAINT uq_user_badges UNIQUE (user_id, badge_id)
);
CREATE INDEX idx_user_badges_user ON user_badges (user_id, awarded_at);

CREATE TABLE user_xp_ledger (
    xp_ledger_id uuid PRIMARY KEY,
    user_id      uuid NOT NULL REFERENCES user_account (user_id),
    source_type  varchar(20) NOT NULL,
    source_id    uuid,
    xp_delta     integer NOT NULL,
    earned_at    timestamp with time zone NOT NULL,
    created_by   uuid,
    created_at   timestamp with time zone NOT NULL,
    updated_by   uuid,
    updated_at   timestamp with time zone NOT NULL
);
CREATE INDEX idx_xp_user_time ON user_xp_ledger (user_id, earned_at);
