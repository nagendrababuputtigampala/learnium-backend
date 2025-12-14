package com.learnium.learniumbackend.logging;

import jakarta.annotation.Nonnull;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class LoggingContextFilter extends OncePerRequestFilter {
    private static final String SESSION_HEADER = "X-Session-Id";
    private static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response, @Nonnull FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String sessionId = request.getHeader(SESSION_HEADER);
            if (sessionId == null || sessionId.isEmpty()) {
                // Generate a new sessionId for login endpoint
                if (request.getRequestURI().equals("/api/auth/session")) {
                    sessionId = UUID.randomUUID().toString();
                }
            }
            String traceId = request.getHeader(TRACE_HEADER);
            if (traceId == null || traceId.isEmpty()) {
                traceId = UUID.randomUUID().toString();
            }
            MDC.put("sessionId", sessionId != null ? sessionId : "");
            MDC.put("traceId", traceId);
            MDC.put("api", request.getMethod() + " " + request.getRequestURI());
            // Propagate IDs in response headers for client correlation
            if (sessionId != null) response.setHeader(SESSION_HEADER, sessionId);
            response.setHeader(TRACE_HEADER, traceId);
            // Propagate CORS headers for all requests (not just when Origin is present)
            String origin = request.getHeader("Origin");
            if (origin != null) {
                response.setHeader("Access-Control-Allow-Origin", origin);
                response.setHeader("Vary", "Origin");
            } else {
                response.setHeader("Access-Control-Allow-Origin", "*");
            }
            response.setHeader("Access-Control-Allow-Credentials", "true");
            response.setHeader("Access-Control-Allow-Headers", "Authorization, X-Session-Id, Content-Type, Accept, sec-ch-ua, sec-ch-ua-mobile, sec-ch-ua-platform, User-Agent, Referer, Origin");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            // Handle preflight OPTIONS requests for CORS
            if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
