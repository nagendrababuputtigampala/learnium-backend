package com.learnium.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Regression guard for the malformed-body log line: Jackson parser messages quote
 * the submitted body, so logging {@code ex.getMessage()} would leak client input
 * (here: a marker token planted in the broken JSON) into the application logs.
 * The handler must log only the code, trace id and exception type — the marker
 * must never appear in captured output, while the 400 envelope is unaffected.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class MalformedBodyLoggingTest {

    private static final String MARKER = "SENSITIVE_MARKER_7f3a9c";

    @Autowired private MockMvc mockMvc;

    @Test
    void malformedBodyMarkerNeverReachesTheLogs(CapturedOutput output) throws Exception {
        mockMvc.perform(
                        post("/test-support/validated")
                                .with(user("tester"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\": " + MARKER + " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-VAL-002"));

        assertThat(output.getAll()).doesNotContain(MARKER);
        // The failure is still logged — with safe metadata only.
        assertThat(output.getAll()).contains("LRN-VAL-002");
    }
}
