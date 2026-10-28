package com.learnium.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates Bearer access tokens inside the security chain (registered
 * before the authorization filter; the servlet-level TraceIdFilter still runs
 * first, so rejections stay correlated). A missing or invalid token is NOT an
 * error here — the request simply stays unauthenticated and the entry point
 * answers with the standard envelope.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            jwtService
                    .validate(token)
                    .ifPresent(
                            principal -> {
                                var authority =
                                        new SimpleGrantedAuthority("ROLE_" + principal.role().name());
                                var authentication =
                                        new UsernamePasswordAuthenticationToken(
                                                principal, null, List.of(authority));
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                            });
        }
        filterChain.doFilter(request, response);
    }
}
