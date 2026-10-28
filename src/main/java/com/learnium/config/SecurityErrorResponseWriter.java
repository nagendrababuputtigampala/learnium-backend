package com.learnium.config;

import com.learnium.common.error.ApiErrorResponse;
import com.learnium.common.error.ErrorCode;
import com.learnium.common.logging.TraceIdFilter;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes the standard {@link ApiErrorResponse} envelope from inside the security
 * filter chain, where {@code GlobalExceptionHandler} cannot reach: MVC advice only
 * sees exceptions thrown by controllers, while authentication and access-denied
 * failures are handled by Spring Security before dispatch. The trace id comes from
 * the same MDC slot the advice reads, so security rejections correlate with the
 * logs exactly like controller errors.
 */
@Component
public class SecurityErrorResponseWriter {

    private static final String TRACE_ID_FALLBACK = "unknown";

    private final ObjectMapper objectMapper;

    public SecurityErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void writeError(HttpServletResponse response, ErrorCode errorCode) throws IOException {
        ApiErrorResponse body =
                ApiErrorResponse.builder()
                        .code(errorCode.getCode())
                        .message(errorCode.getDefaultMessage())
                        .traceId(currentTraceId())
                        .timestamp(Instant.now())
                        .build();
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String currentTraceId() {
        String traceId = MDC.get(TraceIdFilter.MDC_TRACE_ID);
        return traceId != null ? traceId : TRACE_ID_FALLBACK;
    }
}
