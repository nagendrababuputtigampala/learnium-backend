package com.learnium.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.learnium.auth.EmailOutboxService;
import com.learnium.model.auth.EmailOutbox;
import com.learnium.model.auth.EmailOutboxStatus;
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
