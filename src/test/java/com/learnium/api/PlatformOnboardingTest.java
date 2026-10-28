package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learnium.model.auth.EmailOutbox;
import com.learnium.model.user.UserRole;
import com.learnium.repository.auth.EmailOutboxRepository;
import com.learnium.repository.user.UserAccountRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Onboarding with a configured provisioning key: key states + centre creation. */
@SpringBootTest(properties = "learnium.platform.provisioning-key=s3cret-test-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PlatformOnboardingTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private EmailOutboxRepository emailOutboxRepository;

    private static final String BODY =
            "{\"name\":\"New Centre\",\"adminEmail\":\"owner@new.test\",\"adminDisplayName\":\"Olivia Owner\"}";

    @Test
    void correctKeyCreatesCentreAndAdminAndEnqueuesInvite() throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/platform/centres")
                                        .header("X-Provisioning-Key", "s3cret-test-key")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(BODY))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode created = objectMapper.readTree(body);
        String centreId = created.get("centreId").asString();
        String adminUserId = created.get("adminUserId").asString();
        assertThat(centreId).isNotBlank();
        assertThat(adminUserId).isNotBlank();

        var admin = userAccountRepository.findByEmail("owner@new.test");
        assertThat(admin).hasSize(1);
        assertThat(admin.get(0).getRole()).isEqualTo(UserRole.CENTRE_ADMIN);
        assertThat(admin.get(0).getPasswordHash()).isNull();
        assertThat(admin.get(0).getUserId().toString()).isEqualTo(adminUserId);

        List<EmailOutbox> invites =
                emailOutboxRepository.findByCentreId(java.util.UUID.fromString(centreId));
        assertThat(invites).hasSize(1);
        assertThat(invites.get(0).getTemplate()).isEqualTo("invite");
        assertThat(invites.get(0).getRecipient()).isEqualTo("owner@new.test");
    }

    @Test
    void wrongKeyIsRejected401() throws Exception {
        mockMvc.perform(
                        post("/api/v1/platform/centres")
                                .header("X-Provisioning-Key", "wrong-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-001"));
    }

    @Test
    void missingKeyIsRejected401() throws Exception {
        mockMvc.perform(
                        post("/api/v1/platform/centres")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-001"));
    }

    @Test
    void duplicateAdminEmailReturns409() throws Exception {
        mockMvc.perform(
                        post("/api/v1/platform/centres")
                                .header("X-Provisioning-Key", "s3cret-test-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(BODY))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post("/api/v1/platform/centres")
                                .header("X-Provisioning-Key", "s3cret-test-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Second Centre\",\"adminEmail\":\"owner@new.test\",\"adminDisplayName\":\"Other\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LRN-USR-001"));
    }
}
