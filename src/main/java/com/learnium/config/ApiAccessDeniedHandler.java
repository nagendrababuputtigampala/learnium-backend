package com.learnium.config;

import com.learnium.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Authenticated-but-not-allowed requests get the standard error envelope with
 * {@code LRN-AUTH-003}, matching what a controller-thrown forbidden error returns.
 */
@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityErrorResponseWriter errorResponseWriter;

    public ApiAccessDeniedHandler(SecurityErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        errorResponseWriter.writeError(response, ErrorCode.AUTH_FORBIDDEN);
    }
}
