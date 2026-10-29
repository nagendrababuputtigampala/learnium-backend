package com.learnium.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learnium.common.error.ErrorCode;
import com.learnium.config.SecurityErrorResponseWriter;
import com.learnium.model.common.UserStatus;
import com.learnium.model.user.MembershipStatus;
import com.learnium.model.user.UserRole;
import com.learnium.repository.user.BatchMembershipRepository;
import com.learnium.repository.user.UserAccountRepository;
import jakarta.servlet.FilterChain;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * The filter's current-status gate: a valid JWT whose subject was
 * deactivated (or removed) after issuance must stop working immediately.
 */
class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final BatchMembershipRepository batchMembershipRepository =
            mock(BatchMembershipRepository.class);
    private final SecurityErrorResponseWriter errorWriter =
            mock(SecurityErrorResponseWriter.class);
    private final FilterChain chain = mock(FilterChain.class);

    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(
                    jwtService, userAccountRepository, batchMembershipRepository, errorWriter);

    private final UUID userId = UUID.randomUUID();
    private final UUID centreId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest requestWithToken(String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (headerValue != null) {
            request.addHeader("Authorization", headerValue);
        }
        return request;
    }

    private void runFilter(MockHttpServletRequest request) throws Exception {
        filter.doFilter(request, new MockHttpServletResponse(), chain);
    }

    @Test
    void noAuthorizationHeaderLeavesTheRequestUnauthenticated() throws Exception {
        runFilter(requestWithToken(null));

        verify(chain).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void nonBearerHeaderLeavesTheRequestUnauthenticated() throws Exception {
        runFilter(requestWithToken("Basic abc123"));

        verify(chain).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void invalidTokenLeavesTheRequestUnauthenticated() throws Exception {
        when(jwtService.validate("garbage")).thenReturn(Optional.empty());

        runFilter(requestWithToken("Bearer garbage"));

        verify(chain).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void activeStaffTokenAuthenticates() throws Exception {
        when(jwtService.validate("good"))
                .thenReturn(Optional.of(LearniumPrincipal.staff(userId, centreId, UserRole.TEACHER)));
        when(userAccountRepository.findStatusByUserId(userId))
                .thenReturn(Optional.of(UserStatus.ACTIVE));

        runFilter(requestWithToken("Bearer good"));

        verify(chain).doFilter(any(), any());
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getPrincipal())
                .isEqualTo(LearniumPrincipal.staff(userId, centreId, UserRole.TEACHER));
        assertThat(authentication.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_TEACHER");
    }

    @Test
    void deactivatedStaffTokenIsRejectedWith403() throws Exception {
        when(jwtService.validate("good"))
                .thenReturn(Optional.of(LearniumPrincipal.staff(userId, centreId, UserRole.TEACHER)));
        when(userAccountRepository.findStatusByUserId(userId))
                .thenReturn(Optional.of(UserStatus.DISABLED));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithToken("Bearer good"), response, chain);

        verify(errorWriter).writeError(response, ErrorCode.AUTH_ACCOUNT_INACTIVE);
        verify(chain, never()).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void tokenForAMissingAccountIsTreatedAsUnauthenticated() throws Exception {
        when(jwtService.validate("good"))
                .thenReturn(Optional.of(LearniumPrincipal.staff(userId, centreId, UserRole.TEACHER)));
        when(userAccountRepository.findStatusByUserId(userId)).thenReturn(Optional.empty());

        runFilter(requestWithToken("Bearer good"));

        verify(chain).doFilter(any(), any());
        verify(errorWriter, never()).writeError(any(), eq(ErrorCode.AUTH_ACCOUNT_INACTIVE));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void activeStudentTokenAuthenticates() throws Exception {
        UUID batchId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        when(jwtService.validate("student"))
                .thenReturn(
                        Optional.of(
                                LearniumPrincipal.student(userId, centreId, batchId, membershipId)));
        when(batchMembershipRepository.findStatusByCentreIdAndMembershipId(centreId, membershipId))
                .thenReturn(Optional.of(MembershipStatus.ACTIVE));

        runFilter(requestWithToken("Bearer student"));

        verify(chain).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void deactivatedMembershipTokenIsRejectedWith403() throws Exception {
        UUID batchId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        when(jwtService.validate("student"))
                .thenReturn(
                        Optional.of(
                                LearniumPrincipal.student(userId, centreId, batchId, membershipId)));
        when(batchMembershipRepository.findStatusByCentreIdAndMembershipId(centreId, membershipId))
                .thenReturn(Optional.of(MembershipStatus.INACTIVE));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(requestWithToken("Bearer student"), response, chain);

        verify(errorWriter).writeError(response, ErrorCode.AUTH_ACCOUNT_INACTIVE);
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void tokenForAMissingMembershipIsTreatedAsUnauthenticated() throws Exception {
        UUID batchId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();
        when(jwtService.validate("student"))
                .thenReturn(
                        Optional.of(
                                LearniumPrincipal.student(userId, centreId, batchId, membershipId)));
        when(batchMembershipRepository.findStatusByCentreIdAndMembershipId(centreId, membershipId))
                .thenReturn(Optional.empty());

        runFilter(requestWithToken("Bearer student"));

        verify(chain).doFilter(any(), any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
