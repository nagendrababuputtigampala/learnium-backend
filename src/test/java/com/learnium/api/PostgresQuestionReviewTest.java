package com.learnium.api;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** The same API/visibility/edit tests on real PostgreSQL, including nullable filter bindings. */
@EnabledIfEnvironmentVariable(named = "LEARINUM_PG_TEST", matches = "true")
class PostgresQuestionReviewTest extends QuestionReviewFlowTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("LEARINUM_PG_URL"));
        registry.add("spring.datasource.username", () -> System.getenv("LEARINUM_PG_USERNAME"));
        registry.add("spring.datasource.password", () -> System.getenv("LEARINUM_PG_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }
}
