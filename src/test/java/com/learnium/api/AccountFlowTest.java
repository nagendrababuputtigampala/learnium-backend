package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learnium.auth.AccountTokenService;
import com.learnium.model.auth.AccountToken;
import com.learnium.model.auth.AccountTokenPurpose;
import com.learnium.model.auth.EmailOutbox;
import com.learnium.model.common.UserStatus;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.auth.AccountTokenRepository;
import com.learnium.repository.auth.EmailOutboxRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.TokenHashing;
import com.learnium.testsupport.AuthFixtures;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Emailed-link flows over HTTP: forgot/reset password and invite acceptance. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountFlowTest {

    private static final String TEACHER_EMAIL = "teacher@flow.test";
    private static final String OLD_PASSWORD = "OldPass1";

    @Autowired private MockMvc mockMvc;
    @Autowired private CentreRepository centreRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private EmailOutboxRepository emailOutboxRepository;
    @Autowired private AccountTokenRepository accountTokenRepository;
    @Autowired private AccountTokenService accountTokenService;
    @Autowired private PasswordEncoder passwordEncoder;

    private Centre centre;
    private UserAccount invitedTeacher;

    @BeforeEach
    void setUp() {
        centre = AuthFixtures.createCentre(centreRepository, "Flow Centre");
        AuthFixtures.createStaff(
                userAccountRepository, passwordEncoder, centre, UserRole.TEACHER,
                TEACHER_EMAIL, "Tess Teacher", OLD_PASSWORD, UserStatus.ACTIVE);
        AuthFixtures.createStaff(
                userAccountRepository, passwordEncoder, centre, UserRole.TEACHER,
                "inactive@flow.test", "Ian Inactive", OLD_PASSWORD, UserStatus.DISABLED);
        AuthFixtures.createStaff(
                userAccountRepository, passwordEncoder, centre, UserRole.STUDENT,
                "student@flow.test", "Stu Student", OLD_PASSWORD, UserStatus.ACTIVE);
        invitedTeacher =
                AuthFixtures.createStaff(
                        userAccountRepository, passwordEncoder, centre, UserRole.TEACHER,
                        "invited@flow.test", "Ivy Invited", null, UserStatus.ACTIVE);
    }

    private void forgot(String email) throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/forgot-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted());
    }

    private List<EmailOutbox> outboxRows() {
        return emailOutboxRepository.findByCentreId(centre.getCentreId());
    }

    private String rawTokenFromOutbox() {
        List<EmailOutbox> rows = outboxRows();
        assertThat(rows).hasSize(1);
        return rows.get(0).getModel().get("token").asText();
    }

    private void postJson(String path, String json, int expectedStatus) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(expectedStatus));
    }

    @Test
    void forgotPasswordForKnownStaffEnqueuesAResetEmail() throws Exception {
        forgot(TEACHER_EMAIL);

        List<EmailOutbox> rows = outboxRows();
        assertThat(rows).hasSize(1);
        EmailOutbox row = rows.get(0);
        assertThat(row.getRecipient()).isEqualTo(TEACHER_EMAIL);
        assertThat(row.getTemplate()).isEqualTo("reset");
        assertThat(row.getModel().get("purpose").asText()).isEqualTo("reset");
        String raw = rawTokenFromOutbox();
        assertThat(
                        accountTokenRepository.findByTokenHashAndPurpose(
                                TokenHashing.sha256Hex(raw), AccountTokenPurpose.RESET))
                .isPresent();
    }

    @Test
    void forgotPasswordForUnknownEmailStaysSilent() throws Exception {
        forgot("ghost@flow.test");

        assertThat(outboxRows()).isEmpty();
    }

    @Test
    void forgotPasswordForInactiveAccountStaysSilent() throws Exception {
        forgot("inactive@flow.test");

        assertThat(outboxRows()).isEmpty();
    }

    @Test
    void forgotPasswordForStudentAccountStaysSilent() throws Exception {
        forgot("student@flow.test");

        assertThat(outboxRows()).isEmpty();
    }

    @Test
    void resetPasswordFlowChangesThePasswordAndKillsSessions() throws Exception {
        // A live session exists before the reset.
        MvcLogin login = login(TEACHER_EMAIL, OLD_PASSWORD);
        forgot(TEACHER_EMAIL);
        String raw = rawTokenFromOutbox();

        // Weak passwords are rejected by the shared password rule.
        mockMvc.perform(
                        post("/api/v1/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-VAL-001"))
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

        postJson(
                "/api/v1/auth/reset-password",
                "{\"token\":\"" + raw + "\",\"newPassword\":\"NewPass9\"}",
                204);

        // Old password dead, new password works, pre-reset refresh token dead.
        loginExpecting(TEACHER_EMAIL, OLD_PASSWORD, 401);
        login(TEACHER_EMAIL, "NewPass9");
        postJson("/api/v1/auth/refresh", "{\"refreshToken\":\"" + login.refreshToken() + "\"}", 401);

        // The reset token is single-use.
        mockMvc.perform(
                        post("/api/v1/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"Other99x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-004"));
    }

    @Test
    void resetPasswordWithUnknownTokenIsRejected() throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"nope\",\"newPassword\":\"NewPass9\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-004"));
    }

    @Test
    void resetPasswordWithExpiredTokenIsRejected() throws Exception {
        forgot(TEACHER_EMAIL);
        String raw = rawTokenFromOutbox();
        AccountToken token =
                accountTokenRepository
                        .findByTokenHashAndPurpose(TokenHashing.sha256Hex(raw), AccountTokenPurpose.RESET)
                        .orElseThrow();
        token.setExpiresAt(Instant.now().minusSeconds(5));
        accountTokenRepository.save(token);

        mockMvc.perform(
                        post("/api/v1/auth/reset-password")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"NewPass9\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-004"));
    }

    @Test
    void acceptInviteSetsTheFirstPassword() throws Exception {
        String raw = accountTokenService.create(invitedTeacher, AccountTokenPurpose.INVITE, Duration.ofHours(48));

        postJson(
                "/api/v1/auth/accept-invite",
                "{\"token\":\"" + raw + "\",\"newPassword\":\"First1pass\"}",
                204);

        login("invited@flow.test", "First1pass");
    }

    @Test
    void acceptInviteRejectsAResetToken() throws Exception {
        forgot(TEACHER_EMAIL);
        String raw = rawTokenFromOutbox();

        mockMvc.perform(
                        post("/api/v1/auth/accept-invite")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"token\":\"" + raw + "\",\"newPassword\":\"First1pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-004"));
    }

    private record MvcLogin(String refreshToken) {}

    private MvcLogin login(String email, String password) throws Exception {
        String body =
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
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String refreshToken =
                new tools.jackson.databind.ObjectMapper()
                        .readTree(body)
                        .get("refreshToken")
                        .asString();
        return new MvcLogin(refreshToken);
    }

    private void loginExpecting(String email, String password, int expectedStatus) throws Exception {
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\""
                                                + email
                                                + "\",\"password\":\""
                                                + password
                                                + "\"}"))
                .andExpect(status().is(expectedStatus));
    }
}
