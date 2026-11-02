package com.learnium.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.auth.RefreshToken;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import com.learnium.repository.auth.RefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

/**
 * The rotation race, deterministically: the presented token still reads
 * as live, but the atomic consume matches zero rows because a concurrent
 * request won. That is handled exactly like a replay — the whole family
 * is revoked and the request is refused. (The committed-state concurrent
 * test proves the same under real threads; this one pins the branch so
 * coverage never depends on thread timing.)
 */
class RefreshTokenRaceTest {

    @Test
    void losingTheConsumeRaceRevokesTheFamilyAndRefuses() {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        RefreshTokenService service = new RefreshTokenService(repository, transactionManager);

        UUID centreId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Centre centre = new Centre();
        centre.setCentreId(centreId);
        UserAccount user = new UserAccount();
        ReflectionTestUtils.setField(user, "userId", userId);
        user.setCentre(centre);
        user.setSecurityVersion(3L);
        RefreshToken token = new RefreshToken();
        token.setRefreshTokenId(UUID.randomUUID());
        token.setCentreId(centreId);
        token.setUser(user);
        token.setSecurityVersion(3L);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        when(repository.findByTokenHashWithUser(anyString())).thenReturn(Optional.of(token));
        when(repository.consumeIfActive(eq(token.getRefreshTokenId()), any())).thenReturn(0);

        assertThatThrownBy(() -> service.rotate("the-raw-token"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode())
                                .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        verify(repository).consumeIfActive(eq(token.getRefreshTokenId()), any());
        verify(repository).revokeAllByCentreIdAndUserId(eq(centreId), eq(userId), any());
    }
}
