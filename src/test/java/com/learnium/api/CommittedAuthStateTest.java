package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learnium.api.dto.AuthDtos.AuthResponse;
import com.learnium.auth.AccountFlowService;
import com.learnium.auth.AccountTokenService;
import com.learnium.auth.AuthService;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.auth.AccountTokenPurpose;
import com.learnium.model.common.UserStatus;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.auth.RefreshTokenRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.testsupport.AuthFixtures;
import com.learnium.testsupport.CommittedDataCleanup;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * F4 of the M2 fix round: the transaction behaviour the Codex review found
 * broken, proven against COMMITTED state (no wrapping test transaction —
 * the old class-level {@code @Transactional} tests observed uncommitted
 * state and could not see the rollback bugs). PIN lockout persistence,
 * refresh-replay family revocation, and the two concurrency races, driven
 * through the real services and HTTP layer across separate calls/threads.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CommittedAuthStateTest {

    private static final String PASSWORD = "Passw0rd1";
    private static final String TEACHER_EMAIL = "tess@alpha.test";

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private AccountFlowService accountFlowService;
    @Autowired private AccountTokenService accountTokenService;
    @Autowired private CentreRepository centreRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UserAccount teacher;

    @AfterEach
    void cleanUp() {
        CommittedDataCleanup.clearAll(jdbcTemplate);
    }

    @BeforeEach
    void setUp() {
        Centre centre = AuthFixtures.createCentre(centreRepository, "Alpha Centre");
        teacher =
                AuthFixtures.createStaff(
                        userAccountRepository, passwordEncoder, centre, UserRole.TEACHER,
                        TEACHER_EMAIL, "Tess Teacher", PASSWORD, UserStatus.ACTIVE);
    }

    // ---------- refresh replay revocation persists ----------

    @Test
    void refreshReplayRevokesTheWholeFamilyInCommittedState() {
        AuthResponse login = authService.login(TEACHER_EMAIL, PASSWORD);
        AuthResponse rotated = authService.refresh(login.refreshToken());

        // Replaying the rotated-away token is rejected...
        assertThatThrownBy(() -> authService.refresh(login.refreshToken()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex ->
                                assertThat(ex.getErrorCode())
                                        .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));

        // ...and the revocation it triggered is COMMITTED (P1-3 regression
        // proof): the successor issued moments earlier is dead too.
        assertThatThrownBy(() -> authService.refresh(rotated.refreshToken()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex ->
                                assertThat(ex.getErrorCode())
                                        .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
        assertThat(
                        refreshTokenRepository.findAll().stream()
                                .filter(
                                        token ->
                                                token.getUser()
                                                        .getUserId()
                                                        .equals(teacher.getUserId()))
                                .toList())
                .hasSize(2)
                .allMatch(token -> token.getRevokedAt() != null);
    }

    // ---------- (3) + (4) concurrency races ----------

    /** Runs two callables from a common start gate; outcomes are values or the thrown error. */
    private List<Object> raceTwo(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Object> f1 = pool.submit(gated(ready, start, first));
            Future<Object> f2 = pool.submit(gated(ready, start, second));
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(f1.get(60, TimeUnit.SECONDS), f2.get(60, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Object> gated(CountDownLatch ready, CountDownLatch start, Callable<?> body) {
        return () -> {
            ready.countDown();
            start.await(30, TimeUnit.SECONDS);
            try {
                return body.call();
            } catch (Throwable t) {
                return t;
            }
        };
    }

    private static List<Throwable> failuresOf(List<Object> outcomes) {
        return outcomes.stream()
                .filter(Throwable.class::isInstance)
                .map(Throwable.class::cast)
                .toList();
    }

    @Test
    void twoConcurrentRefreshesOfOneTokenExactlyOneSucceeds() throws Exception {
        AuthResponse login = authService.login(TEACHER_EMAIL, PASSWORD);
        String presented = login.refreshToken();

        List<Object> outcomes =
                raceTwo(() -> authService.refresh(presented), () -> authService.refresh(presented));

        List<AuthResponse> successes =
                outcomes.stream()
                        .filter(AuthResponse.class::isInstance)
                        .map(AuthResponse.class::cast)
                        .toList();
        List<Throwable> failures = failuresOf(outcomes);
        assertThat(successes).hasSize(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex ->
                                assertThat(ex.getErrorCode())
                                        .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));

        // The loser's replay handling revoked the family in its own
        // committed transaction: the winner's successor is dead too.
        assertThatThrownBy(() -> authService.refresh(successes.get(0).refreshToken()))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex ->
                                assertThat(ex.getErrorCode())
                                        .isEqualTo(ErrorCode.AUTH_INVALID_CREDENTIALS));
    }

    @Test
    void twoConcurrentResetsWithOneTokenExactlyOnePasswordChangeSucceeds() throws Exception {
        String rawToken =
                accountTokenService.create(teacher, AccountTokenPurpose.RESET, Duration.ofHours(1));

        List<Object> outcomes =
                raceTwo(
                        () -> {
                            accountFlowService.resetPassword(rawToken, "Alpha#12345");
                            return "OK";
                        },
                        () -> {
                            accountFlowService.resetPassword(rawToken, "Bravo#12345");
                            return "OK";
                        });

        List<Object> successes = outcomes.stream().filter("OK"::equals).toList();
        List<Throwable> failures = failuresOf(outcomes);
        assertThat(successes).hasSize(1);
        assertThat(failures).hasSize(1);
        assertThat(failures.get(0))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex ->
                                assertThat(ex.getErrorCode())
                                        .isEqualTo(ErrorCode.AUTH_ACCOUNT_TOKEN_INVALID));

        // Exactly one of the two candidate passwords took effect.
        UserAccount reloaded = userAccountRepository.findById(teacher.getUserId()).orElseThrow();
        boolean alpha = passwordEncoder.matches("Alpha#12345", reloaded.getPasswordHash());
        boolean bravo = passwordEncoder.matches("Bravo#12345", reloaded.getPasswordHash());
        assertThat(alpha ^ bravo).isTrue();
    }
}
