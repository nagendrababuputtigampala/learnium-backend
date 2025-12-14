-- =========================================================
-- Learnium - Minimal Schema (users, subjects, skills)
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
-- USER SKILLS
-- =========================================================
CREATE TABLE IF NOT EXISTS user_skills (
                                           id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name text NOT NULL,
    level int NOT NULL CHECK (level BETWEEN 0 AND 100),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
    );

-- USER CERTIFICATES
-- =========================================================
CREATE TABLE IF NOT EXISTS user_certificates (
                                                 id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title text NOT NULL,
    issuer text NOT NULL,
    date date NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
    );

-- USER EDUCATION
-- =========================================================
CREATE TABLE IF NOT EXISTS user_education (
                                              id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    institution text NOT NULL,
    grade text NOT NULL,
    start_date text NOT NULL,
    current boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
    );

-- USER LINKS
-- =========================================================
CREATE TABLE IF NOT EXISTS user_links (
                                          id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    platform text NOT NULL,
    url text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
    );

-- =========================================================
-- Optional seed data (you can remove if you want)
-- =========================================================
INSERT INTO subjects (code, name)
VALUES
    ('MATH', 'Mathematics'),
    ('PHYSICS', 'Physics')
    ON CONFLICT (code) DO NOTHING;


