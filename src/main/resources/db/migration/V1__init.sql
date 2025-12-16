-- =========================================================
-- Learnium - Full Schema (users, subjects, skills, assessments, etc.)
-- PostgreSQL
-- =========================================================

-- 1) Extensions
CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS citext;    -- case-insensitive text (emails)

-- 2) Common updated_at trigger
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS trigger AS $$
BEGIN
  NEW.updated_at = now();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- =========================================================
-- ENUM TYPES
-- =========================================================
CREATE TYPE image_source AS ENUM ('UPLOAD', 'GOOGLE', 'FACEBOOK', 'DEFAULT');
CREATE TYPE assessment_mode AS ENUM ('PRACTICE', 'TEST', 'QUIZ');
CREATE TYPE question_type AS ENUM ('MCQ', 'FIB', 'SHORT_ANSWER');
CREATE TYPE assessment_item_type AS ENUM ('QUESTION', 'FLASHCARD', 'GAME');
CREATE TYPE attempt_status AS ENUM ('IN_PROGRESS', 'COMPLETED', 'ABANDONED');
CREATE TYPE answer_match_type AS ENUM ('EXACT', 'NUMERIC', 'REGEX');

-- =========================================================
-- USERS
-- =========================================================
CREATE TABLE IF NOT EXISTS users (
                                     id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Firebase unique identity for the user
    firebase_uid    text NOT NULL UNIQUE,

    -- Optional but common
    email           citext,
    display_name    text,

    -- Grade 1 to 12
    grade_level     int NOT NULL CHECK (grade_level BETWEEN 1 AND 12),

    onboarding_done boolean NOT NULL DEFAULT false,

    -- Future-ready fields
    role            text NOT NULL DEFAULT 'STUDENT',  -- STUDENT | PARENT | TEACHER | ADMIN
    status          text NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | SUSPENDED | DELETED

-- New profile fields
    school          text,
    bio             text,
    avatar          text,
    phone           text,
    city            text,
    state           text,
    country         text,
    total_points        int NOT NULL DEFAULT 0,
    current_streak      int NOT NULL DEFAULT 0,
    longest_streak      int NOT NULL DEFAULT 0,
    problems_solved     int NOT NULL DEFAULT 0,
    badges_earned       int NOT NULL DEFAULT 0,
    is_profile_complete boolean NOT NULL DEFAULT false,
    completion_percentage int NOT NULL DEFAULT 0,
    parental_email text,
    profile_image_url text,
    profile_image_source image_source,
    registered_grade smallint,
    last_login_at timestamptz,

    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
    );

-- If you want email unique ONLY when present (recommended)
CREATE UNIQUE INDEX IF NOT EXISTS ux_users_email_not_null
    ON users (email)
    WHERE email IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_users_grade
    ON users (grade_level);

DROP TRIGGER IF EXISTS trg_users_updated_at ON users;
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- SUBJECTS
-- =========================================================
CREATE TABLE IF NOT EXISTS subjects (
                                        id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        text NOT NULL UNIQUE,     -- e.g., MATH, PHYSICS
    name        text NOT NULL,            -- e.g., Mathematics, Physics
    is_active   boolean NOT NULL DEFAULT true,

    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
    );

DROP TRIGGER IF EXISTS trg_subjects_updated_at ON subjects;
CREATE TRIGGER trg_subjects_updated_at
    BEFORE UPDATE ON subjects
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- TOPICS
-- =========================================================
CREATE TABLE IF NOT EXISTS topics (
                                      id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id  uuid NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,

    code        text NOT NULL,            -- e.g., FRACTIONS, ALGEBRA
    name        text NOT NULL,            -- e.g., Fractions, Algebra Basics
    is_active   boolean NOT NULL DEFAULT true,

    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
    );

DROP TRIGGER IF EXISTS trg_topics_updated_at ON topics;
CREATE TRIGGER trg_topics_updated_at
    BEFORE UPDATE ON topics
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- SKILLS (Topics within a subject, scoped by grade range)
-- =========================================================
CREATE TABLE IF NOT EXISTS skills (
                                      id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_id  uuid NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,

    name        text NOT NULL,  -- e.g., Fractions, Algebra Basics
    grade_min   int NOT NULL CHECK (grade_min BETWEEN 1 AND 12),
    grade_max   int NOT NULL CHECK (grade_max BETWEEN 1 AND 12),

    sort_order  int NOT NULL DEFAULT 0,
    is_active   boolean NOT NULL DEFAULT true,

    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),

    -- ensure grade_min <= grade_max
    CONSTRAINT chk_skill_grade_range CHECK (grade_min <= grade_max),

    -- prevent duplicates for same subject and grade range
    CONSTRAINT ux_skill_unique UNIQUE (subject_id, name, grade_min, grade_max)
    );

CREATE INDEX IF NOT EXISTS idx_skills_subject
    ON skills (subject_id);

CREATE INDEX IF NOT EXISTS idx_skills_grade_range
    ON skills (grade_min, grade_max);

DROP TRIGGER IF EXISTS trg_skills_updated_at ON skills;
CREATE TRIGGER trg_skills_updated_at
    BEFORE UPDATE ON skills
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- ASSESSMENTS
-- =========================================================
CREATE TABLE IF NOT EXISTS assessments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    grade_level smallint NOT NULL,
    topic_id uuid NOT NULL REFERENCES topics(id),
    mode assessment_mode NOT NULL,
    title text NOT NULL,
    time_limit_sec integer,
    is_active boolean NOT NULL DEFAULT true,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_assessments_updated_at ON assessments;
CREATE TRIGGER trg_assessments_updated_at
    BEFORE UPDATE ON assessments
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- QUESTIONS
-- =========================================================
CREATE TABLE IF NOT EXISTS questions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id uuid NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    type question_type NOT NULL,
    prompt text NOT NULL,
    difficulty smallint,
    points integer,
    version integer NOT NULL DEFAULT 1,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_questions_updated_at ON questions;
CREATE TRIGGER trg_questions_updated_at
    BEFORE UPDATE ON questions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- QUESTION_OPTIONS
-- =========================================================
CREATE TABLE IF NOT EXISTS question_options (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id uuid NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    option_text text NOT NULL,
    is_correct boolean NOT NULL DEFAULT false,
    sort_order smallint,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_question_options_updated_at ON question_options;
CREATE TRIGGER trg_question_options_updated_at
    BEFORE UPDATE ON question_options
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- FIB_ANSWERS
-- =========================================================
CREATE TABLE IF NOT EXISTS fib_answers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    question_id uuid NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    match_type answer_match_type NOT NULL,
    answer_text text,
    numeric_value numeric,
    tolerance numeric,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_fib_answers_updated_at ON fib_answers;
CREATE TRIGGER trg_fib_answers_updated_at
    BEFORE UPDATE ON fib_answers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- FLASHCARDS
-- =========================================================
CREATE TABLE IF NOT EXISTS flashcards (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id uuid NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    front_text text NOT NULL,
    back_text text NOT NULL,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_flashcards_updated_at ON flashcards;
CREATE TRIGGER trg_flashcards_updated_at
    BEFORE UPDATE ON flashcards
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- GAMES
-- =========================================================
CREATE TABLE IF NOT EXISTS games (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    topic_id uuid NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    game_key text NOT NULL,
    config jsonb,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_games_updated_at ON games;
CREATE TRIGGER trg_games_updated_at
    BEFORE UPDATE ON games
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- ASSESSMENT_ITEMS
-- =========================================================
CREATE TABLE IF NOT EXISTS assessment_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_id uuid NOT NULL REFERENCES assessments(id) ON DELETE CASCADE,
    item_type assessment_item_type NOT NULL,
    sort_order integer,

    -- Polymorphic associations
    question_id uuid REFERENCES questions(id),
    flashcard_id uuid REFERENCES flashcards(id),
    game_id uuid REFERENCES games(id),

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_assessment_items_updated_at ON assessment_items;
CREATE TRIGGER trg_assessment_items_updated_at
    BEFORE UPDATE ON assessment_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- ATTEMPTS
-- =========================================================
CREATE TABLE IF NOT EXISTS attempts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assessment_id uuid NOT NULL REFERENCES assessments(id) ON DELETE CASCADE,
    mode assessment_mode NOT NULL,
    selected_grade smallint,
    status attempt_status NOT NULL,
    score_points integer,
    max_points integer,
    percent_score numeric,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_attempts_updated_at ON attempts;
CREATE TRIGGER trg_attempts_updated_at
    BEFORE UPDATE ON attempts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- ATTEMPT_ITEMS
-- =========================================================
CREATE TABLE IF NOT EXISTS attempt_items (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id uuid NOT NULL REFERENCES attempts(id) ON DELETE CASCADE,
    sort_order integer,

    -- Question details
    question_id uuid REFERENCES questions(id),
    question_version integer,
    prompt_snapshot jsonb,
    is_flagged boolean NOT NULL DEFAULT false,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_attempt_items_updated_at ON attempt_items;
CREATE TRIGGER trg_attempt_items_updated_at
    BEFORE UPDATE ON attempt_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- ATTEMPT_ANSWERS
-- =========================================================
CREATE TABLE IF NOT EXISTS attempt_answers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_item_id uuid UNIQUE NOT NULL REFERENCES attempt_items(id) ON DELETE CASCADE,
    selected_option_id uuid REFERENCES question_options(id),
    answer_text text,
    is_correct boolean,
    awarded_points integer,

    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_attempt_answers_updated_at ON attempt_answers;
CREATE TRIGGER trg_attempt_answers_updated_at
    BEFORE UPDATE ON attempt_answers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- =========================================================
-- USER_SUBJECT_STATS
-- =========================================================
CREATE TABLE IF NOT EXISTS user_subject_stats (
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    grade_level smallint NOT NULL,
    subject_id uuid NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    attempts_count integer,
    avg_score numeric,

    PRIMARY KEY (user_id, grade_level, subject_id)
);

-- =========================================================
-- USER_TOPIC_STATS
-- =========================================================
CREATE TABLE IF NOT EXISTS user_topic_stats (
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    grade_level smallint NOT NULL,
    topic_id uuid NOT NULL REFERENCES topics(id) ON DELETE CASCADE,
    attempts_count integer,
    avg_score numeric,

    PRIMARY KEY (user_id, grade_level, topic_id)
);

-- =========================================================
-- USER_PERCENTILES
-- =========================================================
CREATE TABLE IF NOT EXISTS user_percentiles (
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    grade_level smallint NOT NULL,
    subject_id uuid NOT NULL REFERENCES subjects(id) ON DELETE CASCADE,
    percentile numeric,

    PRIMARY KEY (user_id, grade_level, subject_id)
);

-- =========================================================
-- BADGES
-- =========================================================
CREATE TABLE IF NOT EXISTS badges (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code text UNIQUE NOT NULL,
    name text NOT NULL
);

-- =========================================================
-- USER_BADGES
-- =========================================================
CREATE TABLE IF NOT EXISTS user_badges (
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    badge_id uuid NOT NULL REFERENCES badges(id) ON DELETE CASCADE,
    earned_at timestamptz NOT NULL DEFAULT now(),

    PRIMARY KEY (user_id, badge_id)
);

-- =========================================================
-- Optional seed data (you can remove if you want)
-- =========================================================
INSERT INTO subjects (code, name)
VALUES
    ('MATH', 'Mathematics'),
    ('PHYSICS', 'Physics')
    ON CONFLICT (code) DO NOTHING;


