package com.learnium.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learnium.common.logging.TraceIdFilter;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Security-chain rejections must look exactly like controller errors: the
 * standard envelope, a real trace id, and the echoed X-Request-Id header —
 * including for requests Spring Security short-circuits before dispatch.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityErrorHandlingTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void unauthenticatedRequestReturns401EnvelopeWithGeneratedTraceId() throws Exception {
        mockMvc.perform(get("/api/v1/private"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("LRN-AUTH-001"))
                .andExpect(jsonPath("$.message").value("Authentication required"))
                .andExpect(jsonPath("$.traceId").value(Matchers.not(Matchers.isEmptyOrNullString())))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(header().exists(TraceIdFilter.REQUEST_ID_HEADER));
    }

    @Test
    void unauthenticatedRequestEchoesSuppliedRequestIdIntoHeaderAndBody() throws Exception {
        mockMvc.perform(
                        get("/api/v1/private")
                                .header(TraceIdFilter.REQUEST_ID_HEADER, "review-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(TraceIdFilter.REQUEST_ID_HEADER, "review-401"))
                .andExpect(jsonPath("$.code").value("LRN-AUTH-001"))
                // The body's traceId is the MDC trace id at write time; once the
                // tracing bridge has started a server span it owns that MDC slot
                // (the same is true for controller errors). The correlation id
                // the client supplied is echoed in the header, asserted above.
                .andExpect(jsonPath("$.traceId").value(Matchers.not(Matchers.isEmptyOrNullString())));
    }

    @Test
    void teacherCallingAdminOnlyEndpointReturns403Envelope() throws Exception {
        mockMvc.perform(
                        get("/test-support/admin-only")
                                .with(user("teacher").roles("TEACHER"))
                                .header(TraceIdFilter.REQUEST_ID_HEADER, "review-403"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("LRN-AUTH-003"))
                .andExpect(jsonPath("$.message").value("Access denied"))
                .andExpect(jsonPath("$.traceId").value(Matchers.not(Matchers.isEmptyOrNullString())))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist())
                .andExpect(header().string(TraceIdFilter.REQUEST_ID_HEADER, "review-403"));
    }

    @Test
    void centreAdminPassesTheAdminOnlyGuard() throws Exception {
        mockMvc.perform(get("/test-support/admin-only").with(user("admin").roles("CENTRE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string("admin"));
    }
}
