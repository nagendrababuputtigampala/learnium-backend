package com.learnium.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.learnium.model.user.Centre;
import com.learnium.repository.user.CentreRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The H2 suite proves migrations in PostgreSQL MODE; this test proves them
 * against a real PostgreSQL — the dialect gaps H2 cannot see (constraint
 * enforcement, jsonb, timestamptz) are exactly what the V5/V6 tenancy
 * invariants rely on. It runs ONLY where {@code LEARINUM_PG_TEST=true} (the
 * CI {@code postgres-migration} job provides a postgres:16 service); locally
 * and in the default CI build it is skipped, never failed.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "LEARINUM_PG_TEST", matches = "true")
class PostgresMigrationTest {

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.datasource.url",
                () ->
                        System.getenv()
                                .getOrDefault(
                                        "LEARINUM_PG_URL", "jdbc:postgresql://localhost:5432/learnium"));
        registry.add(
                "spring.datasource.username",
                () -> System.getenv().getOrDefault("LEARINUM_PG_USERNAME", "learnium"));
        registry.add(
                "spring.datasource.password",
                () -> System.getenv().getOrDefault("LEARINUM_PG_PASSWORD", "learnium"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private CentreRepository centreRepository;

    @Test
    void allMigrationsAppliedSuccessfullyOnRealPostgres() {
        Long applied =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM learnium.flyway_schema_history WHERE success = true",
                        Long.class);
        assertThat(applied).isGreaterThanOrEqualTo(7L);
    }

    @Test
    void centreJpaRoundTripOnRealPostgres() {
        Centre centre = new Centre();
        centre.setName("PG Smoke Centre");
        Centre saved = centreRepository.save(centre);
        assertThat(saved.getCentreId()).isNotNull();
        assertThat(centreRepository.findById(saved.getCentreId()))
                .map(Centre::getName)
                .contains("PG Smoke Centre");
    }

    @Test
    void crossCentreMembershipInsertViolatesCompositeFkOnRealPostgres() {
        UUID centreA = insertCentre("PG Centre A");
        UUID centreB = insertCentre("PG Centre B");
        UUID userB = insertUser(centreB);
        UUID batchA = insertBatch(centreA);

        // The roster row claims centre A but joins A's batch to B's student:
        // the (centre_id, user_id) composite FK must reject it on real PG.
        assertThatThrownBy(
                        () ->
                                jdbcTemplate.update(
                                        "INSERT INTO learnium.batch_memberships"
                                                + " (membership_id, centre_id, batch_id, user_id,"
                                                + " member_role, created_at, updated_at)"
                                                + " VALUES (?, ?, ?, ?, 'STUDENT',"
                                                + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                                        UUID.randomUUID(),
                                        centreA,
                                        batchA,
                                        userB))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void crossCentreQuestionInsertViolatesCompositeFkOnRealPostgres() {
        UUID centreA = insertCentre("PG Content Centre A");
        UUID centreB = insertCentre("PG Content Centre B");
        UUID userB = insertUser(centreB);
        UUID materialB = insertMaterial(centreB, userB);

        // The question claims centre A but references B's material: the V6
        // (centre_id, material_id) composite FK must reject it on real PG.
        assertThatThrownBy(() -> insertQuestion(centreA, materialB))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID insertCentre(String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO learnium.centres (centre_id, name, created_at, updated_at)"
                        + " VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id,
                name);
        return id;
    }

    private UUID insertUser(UUID centreId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO learnium.user_account (user_id, centre_id, role, created_at, updated_at)"
                        + " VALUES (?, ?, 'STUDENT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id,
                centreId);
        return id;
    }

    private UUID insertMaterial(UUID centreId, UUID uploaderId) {
        UUID gradeSubjectId =
                jdbcTemplate.queryForObject(
                        "SELECT grade_subject_id FROM learnium.grade_subjects LIMIT 1", UUID.class);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO learnium.materials"
                        + " (material_id, centre_id, uploaded_by, grade_subject_id, title,"
                        + " source_type, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, 'PG Material', 'PDF',"
                        + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id,
                centreId,
                uploaderId,
                gradeSubjectId);
        return id;
    }

    private void insertQuestion(UUID centreId, UUID materialId) {
        UUID gradeSubjectId =
                jdbcTemplate.queryForObject(
                        "SELECT grade_subject_id FROM learnium.grade_subjects LIMIT 1", UUID.class);
        UUID topicId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO learnium.topics"
                        + " (topic_id, grade_subject_id, topic_name, created_at, updated_at)"
                        + " VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                topicId,
                gradeSubjectId,
                "PG Topic " + topicId);
        UUID questionTypeId =
                jdbcTemplate.queryForObject(
                        "SELECT question_type_id FROM learnium.question_types LIMIT 1", UUID.class);
        jdbcTemplate.update(
                "INSERT INTO learnium.questions"
                        + " (question_id, centre_id, topic_id, question_type_id, material_id,"
                        + " payload, correct_ref, created_at, updated_at)"
                        + " VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), 'options[0]',"
                        + " CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                UUID.randomUUID(),
                centreId,
                topicId,
                questionTypeId,
                materialId,
                "{\"prompt\":\"1/2 + 1/4?\"}");
    }

    private UUID insertBatch(UUID centreId) {
        UUID gradeSubjectId =
                jdbcTemplate.queryForObject(
                        "SELECT grade_subject_id FROM learnium.grade_subjects LIMIT 1", UUID.class);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO learnium.batches"
                        + " (batch_id, centre_id, grade_subject_id, name, created_at, updated_at)"
                        + " VALUES (?, ?, ?, 'PG Batch', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                id,
                centreId,
                gradeSubjectId);
        return id;
    }
}
