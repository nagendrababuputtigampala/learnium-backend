# Learnium — as-built entity relationships

Source: PostgreSQL catalogue metadata from the migrated `learnium` schema.
Flyway versions: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13.

Regenerate with `python3 scripts/export_er_diagram.py --container CONTAINER`.

Nullable references and unique child keys determine the diagram cardinalities.
Composite tenancy constraints are listed explicitly below.

```mermaid
erDiagram
    account_token {
        uuid account_token_id PK
        uuid centre_id FK
        uuid user_id FK
        character_varying_20_ purpose
        character_varying_64_ token_hash
        timestamp_with_time_zone expires_at
        timestamp_with_time_zone used_at
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
    }
    background_job {
        uuid id PK
        uuid centre_id FK
        uuid material_id FK
        character_varying_64_ content_hash
        uuid topic_id FK
        jsonb requested_counts
        character_varying_16_ difficulty
        character_varying_16_ provider
        character_varying_64_ model
        character_varying_32_ prompt_version
        character_varying_32_ payload_schema_version
        character_varying_16_ status
        character_varying_32_ failure_kind
        integer attempts
        uuid claim_token
        integer claim_generation
        timestamp_with_time_zone lease_expires_at
        timestamp_with_time_zone next_attempt_at
        jsonb result_counts
        bigint usage_input_tokens
        bigint usage_output_tokens
        bigint usage_thinking_tokens
        character_varying_128_ last_error_class
        timestamp_with_time_zone finished_at
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
    }
    badges {
        uuid badge_id PK
        character_varying_80_ code
        character_varying_150_ name
        character_varying_255_ description
        character_varying_255_ icon
        character_varying_255_ color
        jsonb rule
        boolean is_active
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
    }
    batch_memberships {
        uuid membership_id PK
        uuid batch_id FK
        uuid user_id FK
        character_varying_20_ member_role
        character_varying_255_ pin_hash
        character_varying_20_ status
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
        uuid centre_id FK
        integer pin_failed_attempts
        boolean pin_locked
    }
    batches {
        uuid batch_id PK
        uuid centre_id FK
        uuid grade_subject_id FK
        character_varying_150_ name
        boolean is_active
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
    }
    centres {
        uuid centre_id PK
        character_varying_150_ name
        character_varying_500_ logo_url
        character_varying_255_ contact_email
        character_varying_20_ status
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
    }
    email_outbox {
        uuid email_outbox_id PK
        uuid centre_id FK
        character_varying_255_ recipient
        character_varying_50_ template
        jsonb model
        character_varying_20_ status
        integer attempts
        character_varying_255_ last_error
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
        timestamp_with_time_zone sent_at
    }
    exam_attempts {
        uuid attempt_id PK
        uuid exam_instance_id FK
        uuid user_id FK
        integer attempt_no
        character_varying_20_ status
        timestamp_with_time_zone started_at
        timestamp_with_time_zone submitted_at
        integer total_questions
        integer correct_count
        numeric_38_2_ total_score
        integer integrity_flag_count
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
        uuid centre_id FK
    }
    exam_instance_questions {
        uuid exam_instance_question_id PK
        uuid exam_instance_id FK
        uuid question_id FK
        integer question_order
        integer question_version
        smallint points
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
        uuid centre_id FK
    }
    exam_instances {
        uuid exam_instance_id PK
        uuid exam_template_id FK
        uuid batch_id FK
        uuid published_by FK
        character_varying_20_ join_code
        timestamp_with_time_zone opens_at
        timestamp_with_time_zone closes_at
        character_varying_20_ status
        boolean integrity_signals_on
        character_varying_20_ camera_mode
        boolean retakes_allowed
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
        uuid centre_id FK
    }
    exam_modes {
        uuid exam_mode_id PK
        character_varying_30_ test_type
        character_varying_150_ title
        character_varying_255_ description
        character_varying_255_ icon
        character_varying_255_ button_text
        character_varying_255_ button_color
        character_varying_255_ gradient_from
        character_varying_255_ gradient_to
        jsonb features
        integer sort_order
        boolean is_active
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
    }
    exam_template_questions {
        uuid exam_template_question_id PK
        uuid exam_template_id FK
        uuid question_id FK
        integer question_version
        integer display_order
        smallint points
        character_varying_60_ section
        boolean is_required
        boolean is_active
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
        uuid centre_id FK
    }
    exam_templates {
        uuid exam_template_id PK
        uuid centre_id FK
        uuid grade_id FK
        uuid subject_id FK
        uuid topic_id FK
        uuid exam_mode_id FK
        character_varying_120_ template_name
        character_varying_255_ description
        jsonb rules
        integer version
        boolean is_active
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
    }
    grade_subjects {
        uuid grade_subject_id PK
        uuid grade_id FK
        uuid subject_id FK
        boolean is_active
        integer sort_order
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
    }
    grades {
        uuid grade_id PK
        character_varying_20_ code
        character_varying_100_ name
        integer sort_order
        boolean is_active
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
    }
    materials {
        uuid material_id PK
        uuid centre_id FK
        uuid uploaded_by FK
        uuid grade_subject_id FK
        uuid topic_id FK
        character_varying_200_ title
        character_varying_10_ source_type
        character_varying_500_ storage_key
        text extracted_text
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
        character_varying_64_ content_hash
        character_varying_24_ extraction_status
        integer extracted_chars
        timestamp_with_time_zone deleted_at
        character_varying_64_ dedupe_hash
    }
    question_attempts {
        uuid question_attempt_id PK
        uuid attempt_id FK
        uuid question_id FK
        integer question_version
        jsonb answer_payload
        boolean is_correct
        numeric_38_2_ ai_suggested_score
        numeric_38_2_ confirmed_score
        character_varying_20_ marking_status
        text marker_feedback
        smallint points
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
        uuid centre_id FK
    }
    question_types {
        uuid question_type_id PK
        character_varying_30_ code
        character_varying_60_ name
        character_varying_255_ description
        boolean is_active
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
    }
    questions {
        uuid question_id PK
        uuid centre_id FK
        uuid topic_id FK
        uuid question_type_id FK
        uuid material_id FK
        smallint difficulty
        jsonb payload
        character_varying_50_ correct_ref
        integer version
        character_varying_20_ status
        boolean is_active
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
        uuid generation_job_id FK
        character_varying_16_ generation_provider
        character_varying_64_ generated_by_model
        character_varying_32_ prompt_version
        character_varying_32_ payload_schema_version
        character_varying_64_ content_hash
        uuid approved_by FK
        timestamp_with_time_zone approved_at
        uuid source_question_id FK
        bigint row_revision
    }
    refresh_token {
        uuid refresh_token_id PK
        uuid centre_id FK
        uuid user_id FK
        character_varying_64_ token_hash
        timestamp_with_time_zone expires_at
        timestamp_with_time_zone revoked_at
        timestamp_with_time_zone created_at
        bigint security_version
    }
    student_profiles {
        uuid user_id PK,FK
        uuid grade_id FK
        character_varying_255_ full_name
        date dob
        character_varying_255_ avatar_url
        character_varying_255_ locale
        boolean is_active
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
        uuid centre_id FK
    }
    subjects {
        uuid subject_id PK
        character_varying_120_ subject_name
        character_varying_255_ description
        character_varying_255_ icon
        character_varying_255_ color
        character_varying_255_ gradient_from
        character_varying_255_ gradient_to
        integer sort_order
        boolean is_active
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
    }
    topics {
        uuid topic_id PK
        uuid grade_subject_id FK
        character_varying_200_ topic_name
        character_varying_255_ description
        character_varying_255_ icon
        character_varying_255_ color
        integer sort_order
        boolean is_active
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
    }
    user_account {
        uuid user_id PK
        uuid centre_id FK
        character_varying_20_ role
        character_varying_255_ email
        character_varying_150_ display_name
        character_varying_255_ password_hash
        character_varying_20_ status
        timestamp_with_time_zone last_login_at
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
        character_varying_255_ email_normalized
        bigint security_version
    }
    user_badges {
        uuid user_badge_id PK
        uuid user_id FK
        uuid badge_id FK
        timestamp_with_time_zone awarded_at
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
        uuid centre_id FK
    }
    user_topic_stats {
        uuid user_topic_stats_id PK
        uuid user_id FK
        uuid topic_id FK
        integer attempts
        integer correct
        integer avg_time_ms
        integer xp_total
        timestamp_with_time_zone last_activity_at
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
        uuid centre_id FK
    }
    user_xp_ledger {
        uuid xp_ledger_id PK
        uuid user_id FK
        character_varying_20_ source_type
        uuid source_id
        integer xp_delta
        timestamp_with_time_zone earned_at
        uuid created_by
        timestamp_with_time_zone created_at
        uuid updated_by
        timestamp_with_time_zone updated_at
        uuid centre_id FK
    }
    centres ||--o{ account_token : account_token_centre_id_fkey
    user_account ||--o{ account_token : account_token_user_id_fkey
    user_account ||--o{ account_token : fk_account_token_centre_user
    centres ||--o{ background_job : background_job_centre_id_fkey
    topics ||--o{ background_job : background_job_topic_id_fkey
    materials ||--o{ background_job : fk_background_job_material
    batches ||--o{ batch_memberships : batch_memberships_batch_id_fkey
    user_account ||--o{ batch_memberships : batch_memberships_user_id_fkey
    batches ||--o{ batch_memberships : fk_batch_memberships_centre_batch
    user_account ||--o{ batch_memberships : fk_batch_memberships_centre_user
    centres ||--o{ batches : batches_centre_id_fkey
    grade_subjects ||--o{ batches : batches_grade_subject_id_fkey
    centres ||--o{ email_outbox : email_outbox_centre_id_fkey
    exam_instances ||--o{ exam_attempts : exam_attempts_exam_instance_id_fkey
    user_account ||--o{ exam_attempts : exam_attempts_user_id_fkey
    exam_instances ||--o{ exam_attempts : fk_exam_attempts_centre_instance
    user_account ||--o{ exam_attempts : fk_exam_attempts_centre_user
    exam_instances ||--o{ exam_instance_questions : exam_instance_questions_exam_instance_id_fkey
    questions ||--o{ exam_instance_questions : exam_instance_questions_question_id_fkey
    exam_instances ||--o{ exam_instance_questions : fk_eiq_centre_instance
    questions ||--o{ exam_instance_questions : fk_eiq_centre_question
    batches ||--o{ exam_instances : exam_instances_batch_id_fkey
    exam_templates ||--o{ exam_instances : exam_instances_exam_template_id_fkey
    user_account ||--o{ exam_instances : exam_instances_published_by_fkey
    batches ||--o{ exam_instances : fk_exam_instances_centre_batch
    user_account ||--o{ exam_instances : fk_exam_instances_centre_publisher
    exam_templates ||--o{ exam_instances : fk_exam_instances_centre_template
    exam_templates ||--o{ exam_template_questions : exam_template_questions_exam_template_id_fkey
    questions ||--o{ exam_template_questions : exam_template_questions_question_id_fkey
    questions ||--o{ exam_template_questions : fk_etq_centre_question
    exam_templates ||--o{ exam_template_questions : fk_etq_centre_template
    centres ||--o{ exam_templates : exam_templates_centre_id_fkey
    exam_modes ||--o{ exam_templates : exam_templates_exam_mode_id_fkey
    grades ||--o{ exam_templates : exam_templates_grade_id_fkey
    subjects ||--o{ exam_templates : exam_templates_subject_id_fkey
    topics |o--o{ exam_templates : exam_templates_topic_id_fkey
    grades ||--o{ grade_subjects : grade_subjects_grade_id_fkey
    subjects ||--o{ grade_subjects : grade_subjects_subject_id_fkey
    user_account ||--o{ materials : fk_materials_centre_uploader
    centres ||--o{ materials : materials_centre_id_fkey
    grade_subjects ||--o{ materials : materials_grade_subject_id_fkey
    topics |o--o{ materials : materials_topic_id_fkey
    user_account ||--o{ materials : materials_uploaded_by_fkey
    exam_attempts ||--o{ question_attempts : fk_question_attempts_centre_attempt
    questions ||--o{ question_attempts : fk_question_attempts_centre_question
    exam_attempts ||--o{ question_attempts : question_attempts_attempt_id_fkey
    questions ||--o{ question_attempts : question_attempts_question_id_fkey
    user_account |o--o{ questions : fk_questions_approver_centre
    materials |o--o{ questions : fk_questions_centre_material
    background_job |o--o{ questions : fk_questions_generation_job
    questions |o--o{ questions : fk_questions_source_centre
    centres ||--o{ questions : questions_centre_id_fkey
    materials |o--o{ questions : questions_material_id_fkey
    question_types ||--o{ questions : questions_question_type_id_fkey
    topics ||--o{ questions : questions_topic_id_fkey
    user_account ||--o{ refresh_token : fk_refresh_token_centre_user
    centres ||--o{ refresh_token : refresh_token_centre_id_fkey
    user_account ||--o{ refresh_token : refresh_token_user_id_fkey
    user_account ||--o| student_profiles : fk_student_profiles_centre_user
    grades ||--o{ student_profiles : student_profiles_grade_id_fkey
    user_account ||--o| student_profiles : student_profiles_user_id_fkey
    grade_subjects ||--o{ topics : topics_grade_subject_id_fkey
    centres ||--o{ user_account : user_account_centre_id_fkey
    user_account ||--o{ user_badges : fk_user_badges_centre_user
    badges ||--o{ user_badges : user_badges_badge_id_fkey
    user_account ||--o{ user_badges : user_badges_user_id_fkey
    user_account ||--o{ user_topic_stats : fk_user_topic_stats_centre_user
    topics ||--o{ user_topic_stats : user_topic_stats_topic_id_fkey
    user_account ||--o{ user_topic_stats : user_topic_stats_user_id_fkey
    user_account ||--o{ user_xp_ledger : fk_user_xp_ledger_centre_user
    user_account ||--o{ user_xp_ledger : user_xp_ledger_user_id_fkey
```

## Foreign-key constraints

| Child table | Columns | Parent table | Referenced columns |
|---|---|---|---|
| account_token | centre_id | centres | centre_id |
| account_token | user_id | user_account | user_id |
| account_token | centre_id, user_id | user_account | centre_id, user_id |
| background_job | centre_id | centres | centre_id |
| background_job | topic_id | topics | topic_id |
| background_job | centre_id, material_id | materials | centre_id, material_id |
| batch_memberships | batch_id | batches | batch_id |
| batch_memberships | user_id | user_account | user_id |
| batch_memberships | centre_id, batch_id | batches | centre_id, batch_id |
| batch_memberships | centre_id, user_id | user_account | centre_id, user_id |
| batches | centre_id | centres | centre_id |
| batches | grade_subject_id | grade_subjects | grade_subject_id |
| email_outbox | centre_id | centres | centre_id |
| exam_attempts | exam_instance_id | exam_instances | exam_instance_id |
| exam_attempts | user_id | user_account | user_id |
| exam_attempts | centre_id, exam_instance_id | exam_instances | centre_id, exam_instance_id |
| exam_attempts | centre_id, user_id | user_account | centre_id, user_id |
| exam_instance_questions | exam_instance_id | exam_instances | exam_instance_id |
| exam_instance_questions | question_id | questions | question_id |
| exam_instance_questions | centre_id, exam_instance_id | exam_instances | centre_id, exam_instance_id |
| exam_instance_questions | centre_id, question_id | questions | centre_id, question_id |
| exam_instances | batch_id | batches | batch_id |
| exam_instances | exam_template_id | exam_templates | exam_template_id |
| exam_instances | published_by | user_account | user_id |
| exam_instances | centre_id, batch_id | batches | centre_id, batch_id |
| exam_instances | centre_id, published_by | user_account | centre_id, user_id |
| exam_instances | centre_id, exam_template_id | exam_templates | centre_id, exam_template_id |
| exam_template_questions | exam_template_id | exam_templates | exam_template_id |
| exam_template_questions | question_id | questions | question_id |
| exam_template_questions | centre_id, question_id | questions | centre_id, question_id |
| exam_template_questions | centre_id, exam_template_id | exam_templates | centre_id, exam_template_id |
| exam_templates | centre_id | centres | centre_id |
| exam_templates | exam_mode_id | exam_modes | exam_mode_id |
| exam_templates | grade_id | grades | grade_id |
| exam_templates | subject_id | subjects | subject_id |
| exam_templates | topic_id | topics | topic_id |
| grade_subjects | grade_id | grades | grade_id |
| grade_subjects | subject_id | subjects | subject_id |
| materials | centre_id, uploaded_by | user_account | centre_id, user_id |
| materials | centre_id | centres | centre_id |
| materials | grade_subject_id | grade_subjects | grade_subject_id |
| materials | topic_id | topics | topic_id |
| materials | uploaded_by | user_account | user_id |
| question_attempts | centre_id, attempt_id | exam_attempts | centre_id, attempt_id |
| question_attempts | centre_id, question_id | questions | centre_id, question_id |
| question_attempts | attempt_id | exam_attempts | attempt_id |
| question_attempts | question_id | questions | question_id |
| questions | centre_id, approved_by | user_account | centre_id, user_id |
| questions | centre_id, material_id | materials | centre_id, material_id |
| questions | centre_id, generation_job_id | background_job | centre_id, id |
| questions | centre_id, source_question_id | questions | centre_id, question_id |
| questions | centre_id | centres | centre_id |
| questions | material_id | materials | material_id |
| questions | question_type_id | question_types | question_type_id |
| questions | topic_id | topics | topic_id |
| refresh_token | centre_id, user_id | user_account | centre_id, user_id |
| refresh_token | centre_id | centres | centre_id |
| refresh_token | user_id | user_account | user_id |
| student_profiles | centre_id, user_id | user_account | centre_id, user_id |
| student_profiles | grade_id | grades | grade_id |
| student_profiles | user_id | user_account | user_id |
| topics | grade_subject_id | grade_subjects | grade_subject_id |
| user_account | centre_id | centres | centre_id |
| user_badges | centre_id, user_id | user_account | centre_id, user_id |
| user_badges | badge_id | badges | badge_id |
| user_badges | user_id | user_account | user_id |
| user_topic_stats | centre_id, user_id | user_account | centre_id, user_id |
| user_topic_stats | topic_id | topics | topic_id |
| user_topic_stats | user_id | user_account | user_id |
| user_xp_ledger | centre_id, user_id | user_account | centre_id, user_id |
| user_xp_ledger | user_id | user_account | user_id |
