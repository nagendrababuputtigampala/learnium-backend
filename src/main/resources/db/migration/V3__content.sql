-- V3: curriculum content — topics, question types, uploaded materials, questions.

CREATE TABLE question_types (
    question_type_id uuid PRIMARY KEY,
    code             varchar(30) NOT NULL,
    name             varchar(60) NOT NULL,
    description      varchar(255),
    is_active        boolean NOT NULL DEFAULT true,
    created_by       uuid,
    created_at       timestamp with time zone NOT NULL,
    updated_by       uuid,
    updated_at       timestamp with time zone NOT NULL,
    CONSTRAINT uq_question_types_code UNIQUE (code)
);

-- The five question kinds (fixed UUIDs 30000000-...-01 .. 05).
INSERT INTO question_types (question_type_id, code, name, created_at, updated_at)
VALUES ('30000000-0000-4000-8000-000000000001', 'MCQ', 'Multiple Choice', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO question_types (question_type_id, code, name, created_at, updated_at)
VALUES ('30000000-0000-4000-8000-000000000002', 'FILL_BLANK', 'Fill in the Blank', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO question_types (question_type_id, code, name, created_at, updated_at)
VALUES ('30000000-0000-4000-8000-000000000003', 'FLASHCARD', 'Flashcard', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO question_types (question_type_id, code, name, created_at, updated_at)
VALUES ('30000000-0000-4000-8000-000000000004', 'NUMERIC', 'Numeric Answer', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO question_types (question_type_id, code, name, created_at, updated_at)
VALUES ('30000000-0000-4000-8000-000000000005', 'WRITTEN', 'Written Answer', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

CREATE TABLE topics (
    topic_id         uuid PRIMARY KEY,
    grade_subject_id uuid NOT NULL REFERENCES grade_subjects (grade_subject_id),
    topic_name       varchar(200) NOT NULL,
    description      varchar(255),
    icon             varchar(255),
    color            varchar(255),
    sort_order       integer NOT NULL DEFAULT 0,
    is_active        boolean NOT NULL DEFAULT true,
    created_by       uuid,
    created_at       timestamp with time zone NOT NULL,
    updated_by       uuid,
    updated_at       timestamp with time zone NOT NULL,
    CONSTRAINT uq_topics_name_per_grade_subject UNIQUE (grade_subject_id, topic_name)
);
CREATE INDEX idx_topics_grade_subject ON topics (grade_subject_id, is_active, sort_order);

CREATE TABLE materials (
    material_id      uuid PRIMARY KEY,
    centre_id        uuid NOT NULL REFERENCES centres (centre_id),
    uploaded_by      uuid NOT NULL REFERENCES user_account (user_id),
    grade_subject_id uuid NOT NULL REFERENCES grade_subjects (grade_subject_id),
    topic_id         uuid REFERENCES topics (topic_id),
    title            varchar(200) NOT NULL,
    source_type      varchar(10) NOT NULL,
    storage_key      varchar(500),
    extracted_text   text,
    created_by       uuid,
    created_at       timestamp with time zone NOT NULL,
    updated_by       uuid,
    updated_at       timestamp with time zone NOT NULL
);
CREATE INDEX idx_materials_centre ON materials (centre_id);
CREATE INDEX idx_materials_grade_subject ON materials (grade_subject_id);

CREATE TABLE questions (
    question_id      uuid PRIMARY KEY,
    centre_id        uuid NOT NULL REFERENCES centres (centre_id),
    topic_id         uuid NOT NULL REFERENCES topics (topic_id),
    question_type_id uuid NOT NULL REFERENCES question_types (question_type_id),
    material_id      uuid REFERENCES materials (material_id),
    difficulty       smallint NOT NULL DEFAULT 1,
    payload          jsonb NOT NULL,
    correct_ref      varchar(50) NOT NULL,
    version          integer NOT NULL DEFAULT 1,
    status           varchar(20) NOT NULL DEFAULT 'DRAFT',
    is_active        boolean NOT NULL DEFAULT true,
    created_by       uuid,
    created_at       timestamp with time zone NOT NULL,
    updated_by       uuid,
    updated_at       timestamp with time zone NOT NULL
);
CREATE INDEX idx_questions_topic_active ON questions (topic_id, is_active);
CREATE INDEX idx_questions_topic_difficulty ON questions (topic_id, difficulty, is_active);
CREATE INDEX idx_questions_centre_status ON questions (centre_id, status);
