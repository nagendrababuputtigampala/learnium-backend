package com.learnium.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * With no provisioning key configured (the default), the onboarding endpoint
 * is disabled: it answers 404 exactly as if the route did not exist, whatever
 * header the caller presents.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlatformOnboardingDisabledTest {

    @Autowired private MockMvc mockMvc;

    private static final String BODY =
            "{\"name\":\"New Centre\",\"adminEmail\":\"owner@new.test\",\"adminDisplayName\":\"Olivia Owner\"}";

    @Test
    void anyKeyIsAnswered404WhenUnconfigured() throws Exception {
        mockMvc.perform(
                        post("/api/v1/platform/centres")
                                .header("X-Provisioning-Key", "anything")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-SYS-404"));
    }

    @Test
    void missingKeyIsAnswered404WhenUnconfigured() throws Exception {
        mockMvc.perform(
                        post("/api/v1/platform/centres")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-SYS-404"));
    }
}
