package com.learnium.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.user.UserRole;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.JwtService;
import com.learnium.security.LearniumPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

/** /me for a principal whose account row no longer exists. */
class AuthServiceTest {

    @Test
    void meForAMissingAccountThrowsUnauthenticated() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        UUID centreId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(userAccountRepository.findByCentreIdAndUserId(centreId, userId))
                .thenReturn(Optional.empty());
        AuthService service =
                new AuthService(
                        userAccountRepository,
                        mock(PasswordEncoder.class),
                        mock(JwtService.class),
                        mock(RefreshTokenService.class));
        LearniumPrincipal principal = LearniumPrincipal.staff(userId, centreId, UserRole.TEACHER);

        assertThatThrownBy(() -> service.me(principal))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.AUTH_UNAUTHENTICATED));
    }
}
