package com.learnium.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.learnium.common.error.BusinessException;
import com.learnium.model.auth.AccountToken;
import com.learnium.model.auth.AccountTokenPurpose;
import com.learnium.model.common.UserStatus;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.auth.AccountTokenRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.TokenHashing;
import com.learnium.testsupport.AuthFixtures;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountTokenServiceTest {

    @Autowired private AccountTokenService accountTokenService;
    @Autowired private AccountTokenRepository accountTokenRepository;
    @Autowired private CentreRepository centreRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private UserAccount user;

    @BeforeEach
    void setUp() {
        Centre centre = AuthFixtures.createCentre(centreRepository, "Token Centre");
        user =
                AuthFixtures.createStaff(
                        userAccountRepository, passwordEncoder, centre, UserRole.TEACHER,
                        "teacher@token.test", "Tess Teacher", null, UserStatus.ACTIVE);
    }

    @Test
    void createStoresOnlyTheHashAndConsumeReturnsTheUser() {
        String raw = accountTokenService.create(user, AccountTokenPurpose.INVITE, Duration.ofHours(48));

        AccountToken stored =
                accountTokenRepository
                        .findByTokenHashAndPurpose(TokenHashing.sha256Hex(raw), AccountTokenPurpose.INVITE)
                        .orElseThrow();
        assertThat(stored.getTokenHash()).isNotEqualTo(raw);
        assertThat(stored.getUsedAt()).isNull();

        UserAccount consumed = accountTokenService.consume(raw, AccountTokenPurpose.INVITE);
        assertThat(consumed.getUserId()).isEqualTo(user.getUserId());
        assertThat(stored.getUsedAt()).isNotNull();
    }

    @Test
    void consumeRejectsUnknownToken() {
        assertThatThrownBy(() -> accountTokenService.consume("nope", AccountTokenPurpose.RESET))
                .isInstanceOf(BusinessException.class)
                .hasMessage("This link is invalid or has expired");
    }

    @Test
    void consumeRejectsWrongPurpose() {
        String raw = accountTokenService.create(user, AccountTokenPurpose.INVITE, Duration.ofHours(1));

        assertThatThrownBy(() -> accountTokenService.consume(raw, AccountTokenPurpose.RESET))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void consumeRejectsReplay() {
        String raw = accountTokenService.create(user, AccountTokenPurpose.RESET, Duration.ofHours(1));
        accountTokenService.consume(raw, AccountTokenPurpose.RESET);

        assertThatThrownBy(() -> accountTokenService.consume(raw, AccountTokenPurpose.RESET))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void consumeRejectsExpiredToken() {
        String raw = accountTokenService.create(user, AccountTokenPurpose.RESET, Duration.ofHours(1));
        AccountToken stored =
                accountTokenRepository
                        .findByTokenHashAndPurpose(TokenHashing.sha256Hex(raw), AccountTokenPurpose.RESET)
                        .orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(30));
        accountTokenRepository.save(stored);

        assertThatThrownBy(() -> accountTokenService.consume(raw, AccountTokenPurpose.RESET))
                .isInstanceOf(BusinessException.class);
    }
}
