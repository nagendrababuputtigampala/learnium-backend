-- V2: tenancy core (centres, users, student profiles, batches, roster memberships)
-- plus curriculum master data (grades, subjects, grade-subject pairings).
-- Master rows are seeded here with fixed UUIDs so environments are comparable.

CREATE TABLE grades (
    grade_id   uuid PRIMARY KEY,
    code       varchar(20)  NOT NULL,
    name       varchar(100) NOT NULL,
    sort_order integer      NOT NULL DEFAULT 0,
    is_active  boolean      NOT NULL DEFAULT true,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    updated_by uuid,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT uq_grades_code UNIQUE (code)
);
CREATE INDEX idx_grades_active_sort ON grades (is_active, sort_order);

CREATE TABLE subjects (
    subject_id    uuid PRIMARY KEY,
    subject_name  varchar(120) NOT NULL,
    description   varchar(255),
    icon          varchar(255),
    color         varchar(255),
    gradient_from varchar(255),
    gradient_to   varchar(255),
    sort_order    integer NOT NULL DEFAULT 0,
    is_active     boolean NOT NULL DEFAULT true,
    created_by    uuid,
    created_at    timestamp with time zone NOT NULL,
    updated_by    uuid,
    updated_at    timestamp with time zone NOT NULL,
    CONSTRAINT uq_subjects_name UNIQUE (subject_name)
);
CREATE INDEX idx_subjects_active_sort ON subjects (is_active, sort_order);

CREATE TABLE grade_subjects (
    grade_subject_id uuid PRIMARY KEY,
    grade_id         uuid NOT NULL REFERENCES grades (grade_id),
    subject_id       uuid NOT NULL REFERENCES subjects (subject_id),
    is_active        boolean NOT NULL DEFAULT true,
    sort_order       integer NOT NULL DEFAULT 0,
    created_by       uuid,
    created_at       timestamp with time zone NOT NULL,
    updated_by       uuid,
    updated_at       timestamp with time zone NOT NULL,
    CONSTRAINT uq_grade_subject UNIQUE (grade_id, subject_id)
);
CREATE INDEX idx_grade_subjects_grade ON grade_subjects (grade_id, is_active, sort_order);
CREATE INDEX idx_grade_subjects_subject ON grade_subjects (subject_id);

CREATE TABLE centres (
    centre_id     uuid PRIMARY KEY,
    name          varchar(150) NOT NULL,
    logo_url      varchar(500),
    contact_email varchar(255),
    status        varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_by    uuid,
    created_at    timestamp with time zone NOT NULL,
    updated_by    uuid,
    updated_at    timestamp with time zone NOT NULL
);
CREATE INDEX idx_centres_status ON centres (status);

CREATE TABLE user_account (
    user_id       uuid PRIMARY KEY,
    centre_id     uuid NOT NULL REFERENCES centres (centre_id),
    role          varchar(20) NOT NULL,
    email         varchar(255),
    display_name  varchar(150),
    password_hash varchar(255),
    status        varchar(20) NOT NULL DEFAULT 'ACTIVE',
    last_login_at timestamp with time zone,
    created_by    uuid,
    created_at    timestamp with time zone NOT NULL,
    updated_by    uuid,
    updated_at    timestamp with time zone NOT NULL,
    CONSTRAINT uq_user_account_centre_email UNIQUE (centre_id, email)
);
CREATE INDEX idx_user_account_centre ON user_account (centre_id);
CREATE INDEX idx_user_account_email ON user_account (email);

CREATE TABLE student_profiles (
    user_id    uuid PRIMARY KEY REFERENCES user_account (user_id),
    grade_id   uuid NOT NULL REFERENCES grades (grade_id),
    full_name  varchar(255),
    dob        date,
    avatar_url varchar(255),
    locale     varchar(255),
    is_active  boolean NOT NULL DEFAULT true,
    created_by uuid,
    created_at timestamp with time zone NOT NULL,
    updated_by uuid,
    updated_at timestamp with time zone NOT NULL
);
CREATE INDEX idx_student_profiles_grade ON student_profiles (grade_id);

CREATE TABLE batches (
    batch_id         uuid PRIMARY KEY,
    centre_id        uuid NOT NULL REFERENCES centres (centre_id),
    grade_subject_id uuid NOT NULL REFERENCES grade_subjects (grade_subject_id),
    name             varchar(150) NOT NULL,
    is_active        boolean NOT NULL DEFAULT true,
    created_by       uuid,
    created_at       timestamp with time zone NOT NULL,
    updated_by       uuid,
    updated_at       timestamp with time zone NOT NULL
);
CREATE INDEX idx_batches_centre ON batches (centre_id);

CREATE TABLE batch_memberships (
    membership_id uuid PRIMARY KEY,
    batch_id      uuid NOT NULL REFERENCES batches (batch_id),
    user_id       uuid NOT NULL REFERENCES user_account (user_id),
    member_role   varchar(20) NOT NULL,
    pin_hash      varchar(255),
    status        varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_by    uuid,
    created_at    timestamp with time zone NOT NULL,
    updated_by    uuid,
    updated_at    timestamp with time zone NOT NULL,
    CONSTRAINT uq_batch_memberships_batch_user UNIQUE (batch_id, user_id)
);
CREATE INDEX idx_batch_memberships_batch_status ON batch_memberships (batch_id, status);
CREATE INDEX idx_batch_memberships_user ON batch_memberships (user_id);

-- Master data: grades G1..G12 (fixed UUIDs 10000000-...-01 .. 0c).

INSERT INTO grades (grade_id, code, name, sort_order, is_active, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000001', 'G1', 'Grade 1', 1, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grades (grade_id, code, name, sort_order, is_active, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000002', 'G2', 'Grade 2', 2, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grades (grade_id, code, name, sort_order, is_active, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000003', 'G3', 'Grade 3', 3, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grades (grade_id, code, name, sort_order, is_active, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000004', 'G4', 'Grade 4', 4, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grades (grade_id, code, name, sort_order, is_active, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000005', 'G5', 'Grade 5', 5, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grades (grade_id, code, name, sort_order, is_active, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000006', 'G6', 'Grade 6', 6, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grades (grade_id, code, name, sort_order, is_active, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000007', 'G7', 'Grade 7', 7, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grades (grade_id, code, name, sort_order, is_active, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000008', 'G8', 'Grade 8', 8, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grades (grade_id, code, name, sort_order, is_active, created_at, updated_at) VALUES ('10000000-0000-4000-8000-000000000009', 'G9', 'Grade 9', 9, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grades (grade_id, code, name, sort_order, is_active, created_at, updated_at) VALUES ('10000000-0000-4000-8000-00000000000a', 'G10', 'Grade 10', 10, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grades (grade_id, code, name, sort_order, is_active, created_at, updated_at) VALUES ('10000000-0000-4000-8000-00000000000b', 'G11', 'Grade 11', 11, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grades (grade_id, code, name, sort_order, is_active, created_at, updated_at) VALUES ('10000000-0000-4000-8000-00000000000c', 'G12', 'Grade 12', 12, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Master data: core subjects (fixed UUIDs 20000000-...-01 .. 05).
INSERT INTO subjects (subject_id, subject_name, color, sort_order, is_active, created_at, updated_at) VALUES ('20000000-0000-4000-8000-000000000001', 'Mathematics', '#4338CA', 1, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO subjects (subject_id, subject_name, color, sort_order, is_active, created_at, updated_at) VALUES ('20000000-0000-4000-8000-000000000002', 'Science', '#0D9488', 2, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO subjects (subject_id, subject_name, color, sort_order, is_active, created_at, updated_at) VALUES ('20000000-0000-4000-8000-000000000003', 'English', '#F59E0B', 3, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO subjects (subject_id, subject_name, color, sort_order, is_active, created_at, updated_at) VALUES ('20000000-0000-4000-8000-000000000004', 'Social Studies', '#7C3AED', 4, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO subjects (subject_id, subject_name, color, sort_order, is_active, created_at, updated_at) VALUES ('20000000-0000-4000-8000-000000000005', 'Computer Science', '#2563EB', 5, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Master data: every grade paired with every core subject
-- (fixed UUIDs 40000000-...-<grade hex><subject hex>).
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000101', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000001', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000102', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000002', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000103', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000003', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000104', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000004', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000105', '10000000-0000-4000-8000-000000000001', '20000000-0000-4000-8000-000000000005', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000201', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000001', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000202', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000002', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000203', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000003', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000204', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000004', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000205', '10000000-0000-4000-8000-000000000002', '20000000-0000-4000-8000-000000000005', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000301', '10000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000001', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000302', '10000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000002', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000303', '10000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000003', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000304', '10000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000004', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000305', '10000000-0000-4000-8000-000000000003', '20000000-0000-4000-8000-000000000005', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000401', '10000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000001', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000402', '10000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000002', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000403', '10000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000003', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000404', '10000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000004', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000405', '10000000-0000-4000-8000-000000000004', '20000000-0000-4000-8000-000000000005', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000501', '10000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000001', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000502', '10000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000002', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000503', '10000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000003', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000504', '10000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000004', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000505', '10000000-0000-4000-8000-000000000005', '20000000-0000-4000-8000-000000000005', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000601', '10000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000001', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000602', '10000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000002', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000603', '10000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000003', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000604', '10000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000004', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000605', '10000000-0000-4000-8000-000000000006', '20000000-0000-4000-8000-000000000005', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000701', '10000000-0000-4000-8000-000000000007', '20000000-0000-4000-8000-000000000001', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000702', '10000000-0000-4000-8000-000000000007', '20000000-0000-4000-8000-000000000002', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000703', '10000000-0000-4000-8000-000000000007', '20000000-0000-4000-8000-000000000003', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000704', '10000000-0000-4000-8000-000000000007', '20000000-0000-4000-8000-000000000004', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000705', '10000000-0000-4000-8000-000000000007', '20000000-0000-4000-8000-000000000005', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000801', '10000000-0000-4000-8000-000000000008', '20000000-0000-4000-8000-000000000001', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000802', '10000000-0000-4000-8000-000000000008', '20000000-0000-4000-8000-000000000002', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000803', '10000000-0000-4000-8000-000000000008', '20000000-0000-4000-8000-000000000003', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000804', '10000000-0000-4000-8000-000000000008', '20000000-0000-4000-8000-000000000004', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000805', '10000000-0000-4000-8000-000000000008', '20000000-0000-4000-8000-000000000005', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000901', '10000000-0000-4000-8000-000000000009', '20000000-0000-4000-8000-000000000001', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000902', '10000000-0000-4000-8000-000000000009', '20000000-0000-4000-8000-000000000002', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000903', '10000000-0000-4000-8000-000000000009', '20000000-0000-4000-8000-000000000003', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000904', '10000000-0000-4000-8000-000000000009', '20000000-0000-4000-8000-000000000004', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000905', '10000000-0000-4000-8000-000000000009', '20000000-0000-4000-8000-000000000005', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000a01', '10000000-0000-4000-8000-00000000000a', '20000000-0000-4000-8000-000000000001', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000a02', '10000000-0000-4000-8000-00000000000a', '20000000-0000-4000-8000-000000000002', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000a03', '10000000-0000-4000-8000-00000000000a', '20000000-0000-4000-8000-000000000003', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000a04', '10000000-0000-4000-8000-00000000000a', '20000000-0000-4000-8000-000000000004', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000a05', '10000000-0000-4000-8000-00000000000a', '20000000-0000-4000-8000-000000000005', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000b01', '10000000-0000-4000-8000-00000000000b', '20000000-0000-4000-8000-000000000001', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000b02', '10000000-0000-4000-8000-00000000000b', '20000000-0000-4000-8000-000000000002', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000b03', '10000000-0000-4000-8000-00000000000b', '20000000-0000-4000-8000-000000000003', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000b04', '10000000-0000-4000-8000-00000000000b', '20000000-0000-4000-8000-000000000004', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000b05', '10000000-0000-4000-8000-00000000000b', '20000000-0000-4000-8000-000000000005', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000c01', '10000000-0000-4000-8000-00000000000c', '20000000-0000-4000-8000-000000000001', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000c02', '10000000-0000-4000-8000-00000000000c', '20000000-0000-4000-8000-000000000002', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000c03', '10000000-0000-4000-8000-00000000000c', '20000000-0000-4000-8000-000000000003', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000c04', '10000000-0000-4000-8000-00000000000c', '20000000-0000-4000-8000-000000000004', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
INSERT INTO grade_subjects (grade_subject_id, grade_id, subject_id, is_active, sort_order, created_at, updated_at) VALUES ('40000000-0000-4000-8000-000000000c05', '10000000-0000-4000-8000-00000000000c', '20000000-0000-4000-8000-000000000005', true, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
