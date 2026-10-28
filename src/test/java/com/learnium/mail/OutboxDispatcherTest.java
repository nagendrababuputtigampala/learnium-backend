package com.learnium.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnium.auth.EmailOutboxService;
import com.learnium.model.auth.EmailOutbox;
import com.learnium.model.auth.EmailOutboxStatus;
import com.learnium.repository.auth.EmailOutboxRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dispatcher behaviour with a stub transport: delivery, retry-until-give-up,
 * and failure bookkeeping (exception class names only).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OutboxDispatcherTest {

    /** In-memory transport stub; {@code fail} flips it into throwing mode. */
    static final class RecordingSender implements EmailSender {
        final List<EmailMessage> sent = new ArrayList<>();
        boolean fail;

        @Override
        public void send(EmailMessage message) {
            if (fail) {
                throw new IllegalStateException("stub transport is down");
            }
            sent.add(message);
        }
    }

    @TestConfiguration
    static class StubTransportConfig {
        @Bean
        @Primary
        RecordingSender recordingSender() {
            return new RecordingSender();
        }
    }

    @Autowired private OutboxDispatcher dispatcher;
    @Autowired private EmailOutboxService emailOutboxService;
    @Autowired private EmailOutboxRepository emailOutboxRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RecordingSender recordingSender;

    private final UUID centreId = UUID.randomUUID();

    @BeforeEach
    void resetStub() {
        recordingSender.sent.clear();
        recordingSender.fail = false;
    }

    private Map<String, Object> inviteModel() {
        Map<String, Object> model = new HashMap<>();
        model.put("displayName", "Tess Teacher");
        model.put("centreName", "Bright Minds");
        model.put("token", "raw-token-123");
        model.put("purpose", "invite");
        model.put("expiresHours", "48");
        return model;
    }

    @Test
    void deliversPendingInviteAndMarksItSent() {
        EmailOutbox row =
                emailOutboxService.enqueue(centreId, "tess@bright.test", "invite", inviteModel());

        dispatcher.dispatchPending();

        assertThat(row.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(row.getSentAt()).isNotNull();
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastError()).isNull();
        assertThat(recordingSender.sent).hasSize(1);
        EmailMessage message = recordingSender.sent.get(0);
        assertThat(message.to()).isEqualTo("tess@bright.test");
        assertThat(message.subject()).contains("Bright Minds");
        assertThat(message.htmlBody())
                .contains("/set-password?token=raw-token-123&amp;purpose=invite");
        assertThat(message.textBody())
                .contains("/set-password?token=raw-token-123&purpose=invite")
                .doesNotContain("<p>");
    }

    @Test
    void resetTemplateUsesTheResetSubject() {
        Map<String, Object> model = inviteModel();
        model.put("purpose", "reset");
        emailOutboxService.enqueue(centreId, "tess@bright.test", "reset", model);

        dispatcher.dispatchPending();

        assertThat(recordingSender.sent).hasSize(1);
        assertThat(recordingSender.sent.get(0).subject()).isEqualTo("Reset your Learnium password");
    }

    @Test
    void failingTransportRetriesUntilAttemptsAreExhausted() {
        recordingSender.fail = true;
        EmailOutbox row =
                emailOutboxService.enqueue(centreId, "tess@bright.test", "invite", inviteModel());

        dispatcher.dispatchPending();
        assertThat(row.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(row.getAttempts()).isEqualTo(1);
        assertThat(row.getLastError()).isEqualTo("IllegalStateException");

        dispatcher.dispatchPending();
        dispatcher.dispatchPending();
        assertThat(row.getAttempts()).isEqualTo(3);

        // Given up: further passes leave the row untouched even if transport recovers.
        recordingSender.fail = false;
        dispatcher.dispatchPending();
        assertThat(row.getAttempts()).isEqualTo(3);
        assertThat(row.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(recordingSender.sent).isEmpty();
    }

    @Test
    void unknownTemplateFailsWithTheExceptionClassName() {
        EmailOutbox row = emailOutboxService.enqueue(centreId, "tess@bright.test", "bogus", inviteModel());

        dispatcher.dispatchPending();

        assertThat(row.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(row.getLastError()).isEqualTo("TemplateInputException");
    }

    @Test
    void payloadIsEncryptedAtRestAndWipedAfterDelivery() {
        EmailOutbox row =
                emailOutboxService.enqueue(centreId, "tess@bright.test", "invite", inviteModel());

        // At rest the payload is a ciphertext envelope; the raw token is nowhere in it.
        assertThat(row.getModel().has("enc")).isTrue();
        assertThat(row.getModel().toString()).doesNotContain("raw-token-123");

        dispatcher.dispatchPending();

        assertThat(row.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
        // Delivered secrets do not linger: the payload is wiped on success.
        assertThat(row.getModel().isEmpty()).isTrue();
        assertThat(recordingSender.sent).hasSize(1);
    }

    @Test
    void payloadIsWipedOnceAttemptsAreExhausted() {
        recordingSender.fail = true;
        EmailOutbox row =
                emailOutboxService.enqueue(centreId, "tess@bright.test", "invite", inviteModel());

        dispatcher.dispatchPending();
        // Not yet terminal: the payload survives for the remaining retries.
        assertThat(row.getModel().has("enc")).isTrue();

        dispatcher.dispatchPending();
        dispatcher.dispatchPending();

        assertThat(row.getAttempts()).isEqualTo(3);
        assertThat(row.getStatus()).isEqualTo(EmailOutboxStatus.FAILED);
        assertThat(row.getModel().isEmpty()).isTrue();
    }

    @Test
    void legacyPlaintextRowStillDispatches() {
        ObjectNode plaintext = new ObjectMapper().createObjectNode();
        plaintext.put("displayName", "Tess Teacher");
        plaintext.put("centreName", "Bright Minds");
        plaintext.put("token", "legacy-token-7");
        plaintext.put("purpose", "invite");
        plaintext.put("expiresHours", "48");
        EmailOutbox row = new EmailOutbox();
        row.setCentreId(centreId);
        row.setRecipient("tess@bright.test");
        row.setTemplate("invite");
        row.setModel(plaintext);
        row.setStatus(EmailOutboxStatus.PENDING);
        emailOutboxRepository.save(row);

        dispatcher.dispatchPending();

        assertThat(row.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(recordingSender.sent).hasSize(1);
        assertThat(recordingSender.sent.get(0).textBody()).contains("token=legacy-token-7");
    }

    @Test
    void freshSendingClaimIsNotRedispatched() {
        EmailOutbox row =
                emailOutboxService.enqueue(centreId, "tess@bright.test", "invite", inviteModel());
        row.setStatus(EmailOutboxStatus.SENDING);
        emailOutboxRepository.save(row);

        dispatcher.dispatchPending();

        // Another dispatcher pass owns this row; it is left untouched.
        assertThat(recordingSender.sent).isEmpty();
        assertThat(row.getStatus()).isEqualTo(EmailOutboxStatus.SENDING);
        assertThat(row.getAttempts()).isEqualTo(0);
    }

    @Test
    void staleSendingClaimIsReclaimedAndDelivered() {
        EmailOutbox row =
                emailOutboxService.enqueue(centreId, "tess@bright.test", "invite", inviteModel());
        row.setStatus(EmailOutboxStatus.SENDING);
        emailOutboxRepository.save(row);
        // Flush first: the auditing listener would otherwise bump
        // updated_at back to now when the dispatcher's query auto-flushes,
        // overwriting the backdate below.
        emailOutboxRepository.flush();
        // The claiming pass died ten minutes ago without recording an outcome.
        jdbcTemplate.update(
                "UPDATE learnium.email_outbox SET updated_at = ? WHERE email_outbox_id = ?",
                Timestamp.from(Instant.now().minus(10, ChronoUnit.MINUTES)),
                row.getEmailOutboxId());

        dispatcher.dispatchPending();

        assertThat(recordingSender.sent).hasSize(1);
        assertThat(row.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
    }

    @Test
    void modelValuesOfEveryKindAreConverted() {
        Map<String, Object> model = new HashMap<>();
        model.put("displayName", null);
        model.put("centreName", "Bright Minds");
        model.put("token", "raw-token-9");
        model.put("purpose", "invite");
        model.put("expiresHours", 48);
        EmailOutbox row = emailOutboxService.enqueue(centreId, "tess@bright.test", "invite", model);

        dispatcher.dispatchPending();

        assertThat(row.getStatus()).isEqualTo(EmailOutboxStatus.SENT);
        assertThat(recordingSender.sent).hasSize(1);
        assertThat(recordingSender.sent.get(0).htmlBody()).contains("48");
    }
}
