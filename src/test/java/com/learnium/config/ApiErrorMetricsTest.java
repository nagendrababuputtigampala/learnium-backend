package com.learnium.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiErrorMetricsTest {
    @Autowired private MockMvc mvc;
    @Autowired private MeterRegistry registry;

    private double count(String code, String status) {
        return registry.counter("learnium.api.errors", "code", code, "status", status).count();
    }

    @Test
    void countsSecurityAndControllerFailuresExactlyOnceWithOnlyBoundedTags() throws Exception {
        double unauthenticated = count("LRN-AUTH-001", "401");
        double forbidden = count("LRN-AUTH-003", "403");
        double malformed = count("LRN-VAL-002", "400");
        double unexpected = count("LRN-SYS-001", "500");

        mvc.perform(get("/api/v1/private")).andExpect(status().isUnauthorized());
        mvc.perform(get("/test-support/admin-only").with(user("teacher").roles("TEACHER")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/test-support/validated").with(user("staff"))
                        .contentType(MediaType.APPLICATION_JSON).content("{broken-secret"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/test-support/boom").with(user("staff")))
                .andExpect(status().isInternalServerError());

        assertThat(count("LRN-AUTH-001", "401")).isEqualTo(unauthenticated + 1);
        assertThat(count("LRN-AUTH-003", "403")).isEqualTo(forbidden + 1);
        assertThat(count("LRN-VAL-002", "400")).isEqualTo(malformed + 1);
        assertThat(count("LRN-SYS-001", "500")).isEqualTo(unexpected + 1);
        assertThat(registry.find("learnium.api.errors").meters()).allSatisfy(meter ->
                assertThat(meter.getId().getTags()).allSatisfy(tag ->
                        assertThat(tag.getKey()).isIn("code", "status")));
    }
}
