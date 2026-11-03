package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.*;

@EnabledIfEnvironmentVariable(named = "LEARINUM_PG_TEST", matches = "true")
class PostgresMarkingTest extends MarkingFlowTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("LEARINUM_PG_URL"));
        r.add("spring.datasource.username", () -> System.getenv("LEARINUM_PG_USERNAME"));
        r.add("spring.datasource.password", () -> System.getenv("LEARINUM_PG_PASSWORD"));
        r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "validate"); r.add("spring.flyway.enabled", () -> "true");
    }
    @Test void markingJobCannotPointAtAnAnswerInAnotherCentre() {
        var id = markingJobs.enqueueMarking(answer.getQuestionAttemptId());
        assertThatThrownBy(() -> jdbc.update("UPDATE learnium.background_job SET centre_id = ? WHERE id = ?", otherCentre.getCentreId(), id))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
