package com.learnium.security;

import com.learnium.common.error.ErrorCode;
import com.learnium.config.SecurityErrorResponseWriter;
import com.learnium.model.common.UserStatus;
import com.learnium.model.user.MembershipStatus;
import com.learnium.repository.user.BatchMembershipRepository;
import com.learnium.repository.user.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
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
 *
 * <p>A cryptographically valid token is not enough: the subject's CURRENT
 * status is checked on every request (one indexed projection query). A
 * deactivated staff account — or a student whose roster membership was
 * deactivated — is rejected with 403 {@code LRN-AUTH-005} immediately, not
 * when the token happens to expire; a token whose subject no longer exists
 * is treated as unauthenticated.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;
    private final BatchMembershipRepository batchMembershipRepository;
    private final SecurityErrorResponseWriter errorResponseWriter;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserAccountRepository userAccountRepository,
            BatchMembershipRepository batchMembershipRepository,
            SecurityErrorResponseWriter errorResponseWriter) {
        this.jwtService = jwtService;
        this.userAccountRepository = userAccountRepository;
        this.batchMembershipRepository = batchMembershipRepository;
        this.errorResponseWriter = errorResponseWriter;
    }

    /** What the current-status check decided for a validated token. */
    private enum StatusVerdict {
        AUTHENTICATE,
        SUBJECT_GONE,
        SUBJECT_INACTIVE
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            Optional<LearniumPrincipal> principal = jwtService.validate(token);
            if (principal.isPresent()) {
                StatusVerdict verdict = checkCurrentStatus(principal.get());
                if (verdict == StatusVerdict.SUBJECT_INACTIVE) {
                    errorResponseWriter.writeError(response, ErrorCode.AUTH_ACCOUNT_INACTIVE);
                    return;
                }
                if (verdict == StatusVerdict.AUTHENTICATE) {
                    LearniumPrincipal authenticated = principal.get();
                    var authority =
                            new SimpleGrantedAuthority("ROLE_" + authenticated.role().name());
                    var authentication =
                            new UsernamePasswordAuthenticationToken(
                                    authenticated, null, List.of(authority));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }

    private StatusVerdict checkCurrentStatus(LearniumPrincipal principal) {
        if (principal.isStudent()) {
            Optional<MembershipStatus> status =
                    batchMembershipRepository.findStatusByCentreIdAndMembershipId(
                            principal.centreId(), principal.membershipId());
            if (status.isEmpty()) {
                return StatusVerdict.SUBJECT_GONE;
            }
            return status.get() == MembershipStatus.ACTIVE
                    ? StatusVerdict.AUTHENTICATE
                    : StatusVerdict.SUBJECT_INACTIVE;
        }
        Optional<UserStatus> status = userAccountRepository.findStatusByUserId(principal.userId());
        if (status.isEmpty()) {
            return StatusVerdict.SUBJECT_GONE;
        }
        return status.get() == UserStatus.ACTIVE
                ? StatusVerdict.AUTHENTICATE
                : StatusVerdict.SUBJECT_INACTIVE;
    }
}
