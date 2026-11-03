package com.learnium.migration;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class V14MigrationTest {
    @Test void upgradesExistingExamRowsWithoutLosingOrderMarksOrAnswers() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:publish-upgrade-" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(source).schemas("learnium").target("13").load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID centre = UUID.randomUUID(), actor = UUID.randomUUID(), topic = UUID.randomUUID(), question = UUID.randomUUID(), template = UUID.randomUUID(), instance = UUID.randomUUID();
        var gs = jdbc.queryForMap("SELECT grade_subject_id, grade_id, subject_id FROM learnium.grade_subjects LIMIT 1");
        var type = jdbc.queryForObject("SELECT question_type_id FROM learnium.question_types WHERE code = 'MCQ'", UUID.class);
        UUID mode = UUID.randomUUID();
        jdbc.update("INSERT INTO learnium.exam_modes (exam_mode_id, test_type, title, created_at, updated_at) VALUES (?, 'quiz', 'Legacy quiz', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", mode);
        jdbc.update("INSERT INTO learnium.centres (centre_id, name, created_at, updated_at) VALUES (?, 'Legacy', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", centre);
        jdbc.update("INSERT INTO learnium.user_account (user_id, centre_id, role, display_name, created_at, updated_at) VALUES (?, ?, 'TEACHER', 'Teacher', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", actor, centre);
        jdbc.update("INSERT INTO learnium.topics (topic_id, grade_subject_id, topic_name, created_at, updated_at) VALUES (?, ?, 'Legacy topic', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", topic, gs.get("grade_subject_id"));
        jdbc.update("INSERT INTO learnium.questions (question_id, centre_id, topic_id, question_type_id, payload, correct_ref, created_at, updated_at) VALUES (?, ?, ?, ?, CAST(? AS jsonb), 'a', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", question, centre, topic, type, "{\"stem\":\"Legacy question\"}");
        jdbc.update("INSERT INTO learnium.exam_templates (exam_template_id, centre_id, grade_id, subject_id, exam_mode_id, template_name, rules, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'Legacy test', CAST('{}' AS jsonb), CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", template, centre, gs.get("grade_id"), gs.get("subject_id"), mode);
        jdbc.update("INSERT INTO learnium.exam_template_questions (exam_template_question_id, centre_id, exam_template_id, question_id, display_order, points, created_at, updated_at) VALUES (?, ?, ?, ?, 7, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", UUID.randomUUID(), centre, template, question);
        jdbc.update("INSERT INTO learnium.exam_instances (exam_instance_id, centre_id, exam_template_id, batch_id, published_by, join_code, camera_mode, retakes_allowed, created_at, updated_at) VALUES (?, ?, ?, ?, ?, 'LEGACY88', 'VERIFY_SNAPSHOTS', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", instance, centre, template, batch(jdbc, centre, (UUID) gs.get("grade_subject_id")), actor);
        jdbc.update("INSERT INTO learnium.exam_instance_questions (exam_instance_question_id, centre_id, exam_instance_id, question_id, question_order, points, created_at, updated_at) VALUES (?, ?, ?, ?, 4, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", UUID.randomUUID(), centre, instance, question);
        Flyway.configure().dataSource(source).schemas("learnium").load().migrate();
        assertThat(jdbc.queryForObject("SELECT grade_subject_id FROM learnium.exam_templates WHERE exam_template_id = ?", UUID.class, template)).isEqualTo(gs.get("grade_subject_id"));
        assertThat(jdbc.queryForObject("SELECT marks FROM learnium.exam_template_questions", java.math.BigDecimal.class)).isEqualByComparingTo("2");
        assertThat(jdbc.queryForObject("SELECT display_order FROM learnium.exam_template_questions", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT marks FROM learnium.exam_instance_questions", java.math.BigDecimal.class)).isEqualByComparingTo("3");
        assertThat(jdbc.queryForObject("SELECT correct_ref_snapshot FROM learnium.exam_instance_questions", String.class)).isEqualTo("a");
        assertThat(jdbc.queryForObject("SELECT CAST(payload_snapshot AS varchar) FROM learnium.exam_instance_questions", String.class)).contains("Legacy question");
        var settings = jdbc.queryForMap("SELECT * FROM learnium.exam_instances WHERE exam_instance_id = ?", instance);
        assertThat(settings.get("camera_mode")).isEqualTo("ON"); assertThat(settings.get("max_attempts")).isEqualTo(2);
        assertThat(settings.get("opens_at")).isNotNull(); assertThat(settings.get("closes_at")).isNotNull();
        assertThat(settings.get("definition_name")).isEqualTo("Legacy test");
    }
    private UUID batch(JdbcTemplate jdbc, UUID centre, UUID pairing) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO learnium.batches (batch_id, centre_id, grade_subject_id, name, created_at, updated_at) VALUES (?, ?, ?, 'Legacy batch', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", id, centre, pairing);
        return id;
    }
}
