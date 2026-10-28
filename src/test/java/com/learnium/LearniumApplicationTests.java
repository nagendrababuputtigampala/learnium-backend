package com.learnium;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LearniumApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the full application context (security, JPA, web, logging) starts cleanly.
    }
}
