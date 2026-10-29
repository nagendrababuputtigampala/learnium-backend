package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.learnium.admin.TeacherAdminService;
import com.learnium.api.dto.AuthDtos.AuthResponse;
import com.learnium.auth.AccountFlowService;
import com.learnium.auth.AccountTokenService;
import com.learnium.auth.AuthService;
import com.learnium.auth.RefreshTokenService;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.auth.AccountTokenPurpose;
import com.learnium.model.auth.RefreshToken;
import com.learnium.model.common.UserStatus;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.auth.RefreshTokenRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.LearniumPrincipal;
import com.learnium.security.TokenHashing;
import com.learnium.testsupport.AuthFixtures;
import com.learnium.testsupport.CommittedDataCleanup;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fix round 2 (Codex rerun P1): the security-version proofs, against
 * COMMITTED state like {@link CommittedAuthStateTest}. The race itself —
 * a login whose credential check read the old password just before a
 * reset committed, inserting its refresh token after the reset's
 * revoke-all had already run — cannot be scheduled deterministically
 * with threads, so it is reproduced in its deterministic form: a token
 * row persisted AFTER the credential change, stamped with the
 * pre-change version, exactly as that racing login would have written
 * it. Rotation must refuse it on the version alone.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CommittedSecurityVersionTest {

    private static final String PASSWORD = "Passw0rd1";
    private static final String NEW_PASSWORD = "NewPassw0rd2";
    private static final String TEACHER_EMAIL = "tess@alpha.test";
    private static final String ADMIN_EMAIL = "ada@alpha.test";

    @Autowired private AuthService authService;
    @Autowired private AccountFlowService accountFlowService;
    @Autowired private AccountTokenService accountTokenService;
    @Autowired private TeacherAdminService teacherAdminService;
    @Autowired private CentreRepository centreRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Centre centre;
    private UserAccount teacher;
    private UserAccount admin;

    @AfterEach
    void cleanUp() {
        CommittedDataCleanup.clearAll(jdbcTemplate);
    }

    @BeforeEach
    void setUp() {
        centre = AuthFixtures.createCentre(centreRepository, "Alpha Centre");
        teacher =
                AuthFixtures.createStaff(
                        userAccountRepository, passwordEncoder, centre, UserRole.TEACHER,
                        TEACHER_EMAIL, "Tess Teacher", PASSWORD, UserStatus.ACTIVE);
        admin =
                AuthFixtures.createStaff(
                        userAccountRepository, passwordEncoder, centre, UserRole.CENTRE_ADMIN,
                        ADMIN_EMAIL, "Ada Admin", PASSWORD, UserStatus.ACTIVE);
    }

    private long currentVersionOf(UserAccount user) {
        return userAccountRepository
                .findById(user.getUserId())
                .orElseThrow()
                .getSecurityVersion();
    }

    private UserAccount reload(UserAccount user) {
        return userAccountRepository.findById(user.getUserId()).orElseThrow();
    }

    /**
     * Writes a refresh-token row in exactly the state the racing login
     * would have committed it: live, unexpired, hashed as usual — but
     * stamped with the account's pre-change security version.
     */
    private RefreshToken persistRacingLoginToken(String raw, long stampedVersion) {
        RefreshToken token = new RefreshToken();
        token.setCentreId(centre.getCentreId());
        token.setUser(reload(teacher));
        token.setTokenHash(TokenHashing.sha256Hex(raw));
        token.setSecurityVersion(stampedVersion);
        token.setExpiresAt(Instant.now().plus(RefreshTokenService.REFRESH_TOKEN_TTL));
        token.setCreatedAt(Instant.now());
        return refreshTokenRepository.save(token);
    }

    private void assertRefreshRefused(String rawRefreshToken) {
        assertThatThrownBy(() -> authService.refresh(rawRefreshToken))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex ->
                                assertThat(ex.getErrorCode())
                                        .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
    }

    // ---------- (a) the reset/login race ----------

    @Test
    void tokenStampedBeforeAResetIsRefusedWhenItLandsAfterTheReset() {
        AuthResponse login = authService.login(TEACHER_EMAIL, PASSWORD);
        long preResetVersion = currentVersionOf(teacher);

        // A real password reset, committed.
        String resetRaw =
                accountTokenService.create(teacher, AccountTokenPurpose.RESET, Duration.ofHours(1));
        accountFlowService.resetPassword(resetRaw, NEW_PASSWORD);
        assertThat(currentVersionOf(teacher)).isEqualTo(preResetVersion + 1);

        // The pre-reset login's own token died via the revoke-all.
        assertRefreshRefused(login.refreshToken());

        // The interleave that used to slip through: the racing login's
        // token row lands only now, after the reset committed, stamped
        // with the pre-reset version. It was never revoked — the version
        // alone must refuse it...
        RefreshToken racing = persistRacingLoginToken("racing-login-token", preResetVersion);
        assertRefreshRefused("racing-login-token");

        // ...and the refusal killed the stale token itself, in committed
        // state, without touching anything else.
        assertThat(
                        refreshTokenRepository
                                .findById(racing.getRefreshTokenId())
                                .orElseThrow()
                                .getRevokedAt())
                .isNotNull();
    }

    // ---------- (b) the normal flow is unaffected ----------

    @Test
    void loginAndRotationKeepWorkingWhileVersionsMatch() {
        long version = currentVersionOf(teacher);

        AuthResponse login = authService.login(TEACHER_EMAIL, PASSWORD);
        AuthResponse rotated = authService.refresh(login.refreshToken());
        AuthResponse rotatedTwice = authService.refresh(rotated.refreshToken());

        assertThat(rotatedTwice.accessToken()).isNotBlank();
        assertThat(currentVersionOf(teacher)).isEqualTo(version);
        assertThat(
                        refreshTokenRepository.findAll().stream()
                                .filter(
                                        token ->
                                                token.getUser()
                                                        .getUserId()
                                                        .equals(teacher.getUserId()))
                                .toList())
                .hasSize(3)
                .allMatch(token -> token.getSecurityVersion() == version);
    }

    // ---------- (c) a new login after the reset works ----------

    @Test
    void newLoginAfterResetIssuesTokensThatRefresh() {
        String resetRaw =
                accountTokenService.create(teacher, AccountTokenPurpose.RESET, Duration.ofHours(1));
        accountFlowService.resetPassword(resetRaw, NEW_PASSWORD);

        assertThatThrownBy(() -> authService.login(TEACHER_EMAIL, PASSWORD))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex ->
                                assertThat(ex.getErrorCode())
                                        .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));

        AuthResponse login = authService.login(TEACHER_EMAIL, NEW_PASSWORD);
        AuthResponse rotated = authService.refresh(login.refreshToken());
        assertThat(rotated.refreshToken()).isNotBlank();
        // The post-reset token is stamped with the post-reset version.
        assertThat(
                        refreshTokenRepository
                                .findByTokenHash(TokenHashing.sha256Hex(rotated.refreshToken()))
                                .orElseThrow()
                                .getSecurityVersion())
                .isEqualTo(currentVersionOf(teacher));
    }

    // ---------- (d) deactivation bumps the version too ----------

    @Test
    void deactivationKillsTokensViaRevocationAndViaTheVersion() {
        AuthResponse login = authService.login(TEACHER_EMAIL, PASSWORD);
        long preDeactivationVersion = currentVersionOf(teacher);

        teacherAdminService.deactivateTeacher(
                LearniumPrincipal.staff(
                        admin.getUserId(), centre.getCentreId(), UserRole.CENTRE_ADMIN),
                teacher.getUserId());
        assertThat(currentVersionOf(teacher)).isEqualTo(preDeactivationVersion + 1);

        // The existing behaviour: the live token was revoked outright.
        assertThat(
                        refreshTokenRepository
                                .findByTokenHash(TokenHashing.sha256Hex(login.refreshToken()))
                                .orElseThrow()
                                .getRevokedAt())
                .isNotNull();

        // And the racing-login form — a live token stamped before the
        // deactivation that lands after it — is refused on the version.
        RefreshToken racing =
                persistRacingLoginToken("racing-deactivation-token", preDeactivationVersion);
        assertRefreshRefused("racing-deactivation-token");
        assertThat(
                        refreshTokenRepository
                                .findById(racing.getRefreshTokenId())
                                .orElseThrow()
                                .getRevokedAt())
                .isNotNull();
    }
}
