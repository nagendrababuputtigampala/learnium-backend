package com.learnium.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.learnium.common.error.ErrorCode;
import com.learnium.common.logging.TraceIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class SecurityErrorResponseWriterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final SecurityErrorResponseWriter writer =
            new SecurityErrorResponseWriter(objectMapper);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void writesEnvelopeWithTraceIdFromMdc() throws Exception {
        MDC.put(TraceIdFilter.MDC_TRACE_ID, "trace-123");
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeError(response, ErrorCode.AUTH_UNAUTHENTICATED);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/json");
        String body = response.getContentAsString();
        assertThat(body).contains("\"code\":\"LRN-AUTH-001\"");
        assertThat(body).contains("\"message\":\"Authentication required\"");
        assertThat(body).contains("\"traceId\":\"trace-123\"");
        assertThat(body).contains("\"timestamp\"");
        assertThat(body).doesNotContain("fieldErrors");
    }

    @Test
    void fallsBackToUnknownTraceIdWhenMdcIsEmpty() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.writeError(response, ErrorCode.AUTH_FORBIDDEN);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"traceId\":\"unknown\"");
        assertThat(response.getContentAsString()).contains("\"code\":\"LRN-AUTH-003\"");
    }

    @Test
    void entryPointWrites401Envelope() throws Exception {
        ApiAuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint(writer);
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(
                new MockHttpServletRequest(),
                response,
                new AuthenticationException("no credentials") {});

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"LRN-AUTH-001\"");
    }

    @Test
    void accessDeniedHandlerWrites403Envelope() throws Exception {
        ApiAccessDeniedHandler handler = new ApiAccessDeniedHandler(writer);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(
                new MockHttpServletRequest(),
                response,
                new AccessDeniedException("wrong role"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"LRN-AUTH-003\"");
    }
}
