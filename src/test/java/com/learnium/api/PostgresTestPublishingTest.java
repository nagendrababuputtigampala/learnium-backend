package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;

@EnabledIfEnvironmentVariable(named = "LEARINUM_PG_TEST", matches = "true")
class PostgresTestPublishingTest extends TestPublishingFlowTest {
    @Autowired JdbcTemplate jdbc;
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("LEARINUM_PG_URL"));
        r.add("spring.datasource.username", () -> System.getenv("LEARINUM_PG_USERNAME"));
        r.add("spring.datasource.password", () -> System.getenv("LEARINUM_PG_PASSWORD"));
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate"); r.add("spring.flyway.enabled", () -> "true");
    }
    @Test void assignedStudentsCannotReferenceAnotherCentreActor() throws Exception {
        var instance = publish(create().path("id").asText(), publishBody());
        var snapshot = assigned.assigned(centre.getCentreId(), UUID.fromString(instance.path("id").asText())).get(0);
        assertThatThrownBy(() -> jdbc.update("UPDATE learnium.exam_instance_student SET added_by = ? WHERE id = ?", foreign.getUserId(), snapshot.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void assignedStudentsCannotBeReparentedAcrossCentres() throws Exception {
        var instance = publish(create().path("id").asText(), publishBody());
        var snapshot = assigned.assigned(centre.getCentreId(), UUID.fromString(instance.path("id").asText())).get(0);
        assertThatThrownBy(() -> jdbc.update("UPDATE learnium.exam_instance_student SET centre_id = ? WHERE id = ?", otherCentre.getCentreId(), snapshot.getId()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
