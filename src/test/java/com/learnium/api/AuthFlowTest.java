package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learnium.model.auth.RefreshToken;
import com.learnium.model.common.UserStatus;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.auth.RefreshTokenRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.JwtService;
import com.learnium.security.TokenHashing;
import com.learnium.testsupport.AuthFixtures;
import com.learnium.testsupport.CommittedDataCleanup;
import java.time.Instant;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end staff auth: login, /me, refresh rotation + reuse detection,
 * logout.
 *
 * <p>Committed-state class (no wrapping test transaction): rotation,
 * replay detection and revocation are cross-request, cross-transaction
 * behaviours — inside a class-level {@code @Transactional} they only ever
 * observed uncommitted state, which is how the rollback bugs shipped.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AuthFlowTest {

    private static final String ADMIN_EMAIL = "admin@auth.test";
    private static final String PASSWORD = "Passw0rd1";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CentreRepository centreRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Centre centre;

    @AfterEach
    void cleanUp() {
        CommittedDataCleanup.clearAll(jdbcTemplate);
    }

    @BeforeEach
    void setUp() {
        centre = AuthFixtures.createCentre(centreRepository, "Auth Centre");
        AuthFixtures.createStaff(
                userAccountRepository, passwordEncoder, centre, UserRole.CENTRE_ADMIN,
                ADMIN_EMAIL, "Ann Admin", PASSWORD, UserStatus.ACTIVE);
        AuthFixtures.createStaff(
                userAccountRepository, passwordEncoder, centre, UserRole.TEACHER,
                "inactive@auth.test", "Ian Inactive", PASSWORD, UserStatus.DISABLED);
        AuthFixtures.createStaff(
                userAccountRepository, passwordEncoder, centre, UserRole.TEACHER,
                "nopassword@auth.test", "No Password", null, UserStatus.ACTIVE);
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\""
                                                        + email
                                                        + "\",\"password\":\""
                                                        + password
                                                        + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode refresh(String refreshToken, int expectedStatus) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post("/api/v1/auth/refresh")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                        .andExpect(status().is(expectedStatus))
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void loginReturnsTokensAndUserSummary() throws Exception {
        JsonNode body = login(ADMIN_EMAIL, PASSWORD);

        assertThat(body.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(body.get("expiresInSeconds").asLong()).isEqualTo(900L);
        assertThat(body.get("accessToken").asString()).isNotBlank();
        assertThat(body.get("refreshToken").asString()).isNotBlank();
        assertThat(body.get("user").get("email").asString()).isEqualTo(ADMIN_EMAIL);
        assertThat(body.get("user").get("displayName").asString()).isEqualTo("Ann Admin");
        assertThat(body.get("user").get("role").asString()).isEqualTo("CENTRE_ADMIN");
        assertThat(body.get("user").get("centreId").asString())
                .isEqualTo(centre.getCentreId().toString());
        assertThat(body.get("user").get("centreName").asString()).isEqualTo("Auth Centre");
        // Only the hash of the refresh token is stored.
        String raw = body.get("refreshToken").asString();
        assertThat(refreshTokenRepository.findByTokenHash(TokenHashing.sha256Hex(raw))).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash(raw)).isEmpty();
    }

    @Test
    void accessTokenAuthenticatesMe() throws Exception {
        JsonNode body = login(ADMIN_EMAIL, PASSWORD);
        String accessToken = body.get("accessToken").asString();

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.role").value("CENTRE_ADMIN"))
                .andExpect(jsonPath("$.centreName").value("Auth Centre"));
    }

    @Test
    void loginWithWrongPasswordReturnsInvalidCredentials() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\""
                                                + ADMIN_EMAIL
                                                + "\",\"password\":\"WrongPass1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-002"));
    }

    @Test
    void loginWithUnknownEmailReturnsSameInvalidCredentials() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"ghost@auth.test\",\"password\":\"Passw0rd1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-002"));
    }

    @Test
    void loginForAccountWithoutPasswordReturnsInvalidCredentials() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"nopassword@auth.test\",\"password\":\"Passw0rd1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-002"));
    }

    @Test
    void loginForInactiveAccountWithCorrectPasswordReturnsAccountInactive() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"inactive@auth.test\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-005"));
    }

    @Test
    void loginValidationFailureReturnsFieldErrors() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"not-an-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-VAL-001"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void refreshRotatesTheToken() throws Exception {
        String first = login(ADMIN_EMAIL, PASSWORD).get("refreshToken").asString();

        JsonNode rotated = refresh(first, 200);
        String second = rotated.get("refreshToken").asString();
        assertThat(second).isNotEqualTo(first);
        assertThat(rotated.get("accessToken").asString()).isNotBlank();

        JsonNode rotatedAgain = refresh(second, 200);
        assertThat(rotatedAgain.get("refreshToken").asString()).isNotEqualTo(second);
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() throws Exception {
        String first = login(ADMIN_EMAIL, PASSWORD).get("refreshToken").asString();
        String second = refresh(first, 200).get("refreshToken").asString();

        // Replay of the already-rotated token: rejected...
        JsonNode replay = refresh(first, 401);
        assertThat(replay.get("code").asString()).isEqualTo("LRN-AUTH-002");
        // ...and the legitimately rotated successor is dead too.
        refresh(second, 401);
    }

    @Test
    void refreshWithUnknownTokenIsRejected() throws Exception {
        JsonNode body = refresh("no-such-token", 401);
        assertThat(body.get("code").asString()).isEqualTo("LRN-AUTH-002");
    }

    @Test
    void refreshWithExpiredTokenIsRejected() throws Exception {
        String raw = login(ADMIN_EMAIL, PASSWORD).get("refreshToken").asString();
        RefreshToken stored =
                refreshTokenRepository.findByTokenHash(TokenHashing.sha256Hex(raw)).orElseThrow();
        stored.setExpiresAt(Instant.now().minusSeconds(60));
        refreshTokenRepository.save(stored);

        refresh(raw, 401);
    }

    @Test
    void logoutRevokesThePresentedToken() throws Exception {
        JsonNode body = login(ADMIN_EMAIL, PASSWORD);
        String accessToken = body.get("accessToken").asString();
        String refreshToken = body.get("refreshToken").asString();

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(status().isNoContent());

        refresh(refreshToken, 401);
    }

    @Test
    void logoutTwiceWithTheSameTokenStillReturns204() throws Exception {
        JsonNode body = login(ADMIN_EMAIL, PASSWORD);
        String accessToken = body.get("accessToken").asString();
        String refreshToken = body.get("refreshToken").asString();
        String logoutBody = "{\"refreshToken\":\"" + refreshToken + "\"}";

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(logoutBody))
                .andExpect(status().isNoContent());
        // The token is already revoked; logging out again is a no-op, not an error.
        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(logoutBody))
                .andExpect(status().isNoContent());
    }

    @Test
    void logoutWithUnknownTokenStillReturns204() throws Exception {
        String accessToken = login(ADMIN_EMAIL, PASSWORD).get("accessToken").asString();

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .header("Authorization", "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"never-issued\"}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void refreshAfterDeactivationReturnsAccountInactive() throws Exception {
        String refreshToken = login(ADMIN_EMAIL, PASSWORD).get("refreshToken").asString();
        var admin =
                userAccountRepository.findByEmail(ADMIN_EMAIL).stream()
                        .filter(u -> u.getCentre().getCentreId().equals(centre.getCentreId()))
                        .findFirst()
                        .orElseThrow();
        admin.setStatus(UserStatus.DISABLED);
        userAccountRepository.save(admin);

        JsonNode body = refresh(refreshToken, 403);
        assertThat(body.get("code").asString()).isEqualTo("LRN-AUTH-005");
    }

    @Test
    void loginWithSameEmailAndPasswordInTwoCentresIsRejected() throws Exception {
        Centre other = AuthFixtures.createCentre(centreRepository, "Other Centre");
        AuthFixtures.createStaff(
                userAccountRepository, passwordEncoder, other, UserRole.CENTRE_ADMIN,
                ADMIN_EMAIL, "Other Admin", PASSWORD, UserStatus.ACTIVE);

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\""
                                                + ADMIN_EMAIL
                                                + "\",\"password\":\""
                                                + PASSWORD
                                                + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-002"));
    }

    @Test
    void deactivatedAccountsAccessTokenStopsWorkingImmediately() throws Exception {
        JsonNode body = login(ADMIN_EMAIL, PASSWORD);
        String accessToken = body.get("accessToken").asString();
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        // Deactivation lands after the token was issued; the very next
        // request must already fail — the token's remaining lifetime is
        // irrelevant.
        UserAccount admin = userAccountRepository.findByEmail(ADMIN_EMAIL).get(0);
        admin.setStatus(UserStatus.DISABLED);
        userAccountRepository.save(admin);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-005"));
    }

    @Test
    void accessTokenWhoseAccountNoLongerExistsIsUnauthenticated() throws Exception {
        // A well-formed token for an account row that does not exist.
        Centre ghostCentre = new Centre();
        ghostCentre.setCentreId(java.util.UUID.randomUUID());
        ghostCentre.setName("Ghost Centre");
        UserAccount ghost = new UserAccount();
        ghost.setUserId(java.util.UUID.randomUUID());
        ghost.setCentre(ghostCentre);
        ghost.setRole(UserRole.TEACHER);
        String token = jwtService.issueStaffToken(ghost);

        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-001"));
    }

    @Test
    void meWithNonBearerAuthorizationHeaderReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Basic abc123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-001"));
    }

    @Test
    void meWithoutTokenReturns401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-001"));
    }

    @Test
    void meWithGarbageTokenReturns401Envelope() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer garbage"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-001"));
    }
}
