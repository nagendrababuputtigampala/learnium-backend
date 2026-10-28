package com.learnium.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.learnium.model.user.Centre;
import com.learnium.repository.user.CentreRepository;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves the Flyway migrations execute cleanly in order (H2 in PostgreSQL mode)
 * and that the migrated schema accepts entity writes — startup already proved
 * Hibernate {@code ddl-auto: validate} against it.
 */
@SpringBootTest
@ActiveProfiles("migration")
class MigrationTest {

    @Autowired private Flyway flyway;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CentreRepository centreRepository;

    @Test
    void allMigrationsAppliedSuccessfullyInOrder() {
        // applied() also carries Flyway's null-version schema marker row; keep real migrations.
        List<MigrationInfo> applied =
                java.util.Arrays.stream(flyway.info().applied())
                        .filter(info -> info.getVersion() != null)
                        .toList();
        assertThat(applied)
                .extracting(MigrationInfo::getVersion)
                .extracting(Object::toString)
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9");
        assertThat(applied)
                .extracting(MigrationInfo::getState)
                .containsOnly(MigrationState.SUCCESS);
    }

    @Test
    void tenancyAndContentTablesExist() {
        List<String> expected =
                List.of(
                        "centres",
                        "user_account",
                        "student_profiles",
                        "grades",
                        "subjects",
                        "grade_subjects",
                        "batches",
                        "batch_memberships",
                        "topics",
                        "question_types",
                        "materials",
                        "questions",
                        "exam_modes",
                        "exam_templates",
                        "exam_template_questions",
                        "exam_instances",
                        "exam_instance_questions",
                        "exam_attempts",
                        "question_attempts",
                        "user_topic_stats",
                        "badges",
                        "user_badges",
                        "user_xp_ledger",
                        "account_token",
                        "email_outbox",
                        "refresh_token");
        List<String> actual =
                jdbcTemplate.queryForList(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES"
                                + " WHERE UPPER(TABLE_SCHEMA) = 'LEARNIUM'",
                        String.class);
        assertThat(actual.stream().map(String::toLowerCase).toList())
                .containsAll(expected);
    }

    @Test
    void masterDataSeededByMigrations() {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM learnium.grades", Long.class))
                .isEqualTo(12L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM learnium.subjects", Long.class))
                .isEqualTo(5L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM learnium.grade_subjects", Long.class))
                .isEqualTo(60L);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM learnium.question_types", Long.class))
                .isEqualTo(5L);
        assertThat(
                        jdbcTemplate.queryForList(
                                "SELECT code FROM learnium.question_types ORDER BY code",
                                String.class))
                .containsExactly("FILL_BLANK", "FLASHCARD", "MCQ", "NUMERIC", "WRITTEN");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT code FROM learnium.grades WHERE grade_id ="
                                        + " '10000000-0000-4000-8000-000000000008'",
                                String.class))
                .isEqualTo("G8");
    }

    @Test
    void staffEmailIsGloballyUniqueCaseInsensitivelyAfterV8() {
        java.util.UUID centreA = insertCentreRow("Email Centre A");
        java.util.UUID centreB = insertCentreRow("Email Centre B");
        insertUserRow(centreA, "shared@example.test");

        // The same email in ANOTHER centre is rejected, in any letter case:
        // the V8 expression index on lower(email) is the database backstop
        // behind the services' friendly pre-check.
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> insertUserRow(centreB, "SHARED@example.test"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        // And, trivially, within the same centre too (the old V2 rule).
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> insertUserRow(centreA, "shared@example.test"))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        // NULL emails (students) are unrestricted by the index.
        insertUserRow(centreA, null);
        insertUserRow(centreB, null);
    }

    private java.util.UUID insertCentreRow(String name) {
        java.util.UUID centreId = java.util.UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO learnium.centres"
                        + " (centre_id, name, status, created_at, updated_at)"
                        + " VALUES (?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                centreId,
                name);
        return centreId;
    }

    private void insertUserRow(java.util.UUID centreId, String email) {
        jdbcTemplate.update(
                "INSERT INTO learnium.user_account"
                        + " (user_id, centre_id, role, email, display_name, status,"
                        + " created_at, updated_at)"
                        + " VALUES (?, ?, 'TEACHER', ?, 'Email Test', 'ACTIVE',"
                        + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                java.util.UUID.randomUUID(),
                centreId,
                email);
    }

    @Test
    void migratedSchemaAcceptsEntityWrites() {
        Centre centre = new Centre();
        centre.setName("Migration Smoke Centre");
        Centre saved = centreRepository.save(centre);
        assertThat(saved.getCentreId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(centreRepository.findById(saved.getCentreId())).isPresent();
    }
}
