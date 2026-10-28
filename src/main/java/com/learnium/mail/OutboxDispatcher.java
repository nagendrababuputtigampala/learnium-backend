package com.learnium.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnium.model.auth.EmailOutbox;
import com.learnium.model.auth.EmailOutboxStatus;
import com.learnium.repository.auth.EmailOutboxRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Delivers outbox rows: claims each row atomically (a conditional
 * PENDING/FAILED/stale-SENDING → SENDING flip, so two dispatcher instances
 * can never send the same row), decrypts its payload, renders the template,
 * sends via {@link EmailSender}, and records the outcome in a short
 * transaction. Failures are retried on later passes until
 * {@link #MAX_ATTEMPTS} attempts; {@code lastError} stores the exception
 * class name only — never a message, which could carry recipient input.
 * Terminal rows (SENT, or FAILED with attempts exhausted) have their
 * payload wiped: a delivered or dead set-password token must not linger.
 */
@Component
public class OutboxDispatcher {

    public static final int MAX_ATTEMPTS = 3;
    private static final int BATCH_SIZE = 20;
    private static final Duration SENDING_STALE_AFTER = Duration.ofMinutes(5);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final EmailOutboxRepository emailOutboxRepository;
    private final MailTemplateRenderer renderer;
    private final EmailSender emailSender;
    private final PayloadCipher payloadCipher;
    private final String publicBaseUrl;
    private final TransactionTemplate transactionTemplate;

    public OutboxDispatcher(
            EmailOutboxRepository emailOutboxRepository,
            MailTemplateRenderer renderer,
            EmailSender emailSender,
            PayloadCipher payloadCipher,
            @Value("${learnium.app.public-base-url:http://localhost:5173}") String publicBaseUrl,
            PlatformTransactionManager transactionManager) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.renderer = renderer;
        this.emailSender = emailSender;
        this.payloadCipher = payloadCipher;
        this.publicBaseUrl = publicBaseUrl;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${learnium.mail.dispatch-delay-ms:5000}")
    public void dispatchPending() {
        Instant staleBefore = Instant.now().minus(SENDING_STALE_AFTER);
        for (UUID id :
                emailOutboxRepository.findDispatchCandidateIds(
                        MAX_ATTEMPTS, staleBefore, PageRequest.of(0, BATCH_SIZE))) {
            Boolean claimed =
                    transactionTemplate.execute(
                            status ->
                                    emailOutboxRepository.claimForSending(
                                                    id,
                                                    MAX_ATTEMPTS,
                                                    staleBefore,
                                                    Instant.now())
                                            == 1);
            if (Boolean.TRUE.equals(claimed)) {
                dispatchOne(id);
            }
        }
    }

    private void dispatchOne(UUID id) {
        emailOutboxRepository.findById(id).ifPresent(this::deliverClaimed);
    }

    private void deliverClaimed(EmailOutbox row) {
        int attempts = row.getAttempts() + 1;
        try {
            Map<String, Object> model = toModel(payloadCipher.decrypt(row.getModel()));
            model.put("link", buildSetPasswordLink(model));
            String subject = subjectFor(row.getTemplate(), model);
            String html = renderer.render(row.getTemplate(), model);
            emailSender.send(
                    new EmailMessage(
                            row.getRecipient(), subject, html, renderer.toPlainText(html)));
            row.setStatus(EmailOutboxStatus.SENT);
            row.setSentAt(Instant.now());
            row.setLastError(null);
            row.setModel(JSON.createObjectNode());
        } catch (RuntimeException e) {
            row.setStatus(EmailOutboxStatus.FAILED);
            row.setLastError(e.getClass().getSimpleName());
            if (attempts >= MAX_ATTEMPTS) {
                row.setModel(JSON.createObjectNode());
            }
            log.warn(
                    "Outbox dispatch failed for {} (attempt {}, cause {})",
                    row.getEmailOutboxId(),
                    attempts,
                    e.getClass().getSimpleName());
        }
        row.setAttempts(attempts);
        transactionTemplate.executeWithoutResult(status -> emailOutboxRepository.save(row));
    }

    private String buildSetPasswordLink(Map<String, Object> model) {
        return publicBaseUrl
                + "/set-password?token="
                + model.getOrDefault("token", "")
                + "&purpose="
                + model.getOrDefault("purpose", "");
    }

    private String subjectFor(String template, Map<String, Object> model) {
        return switch (template) {
            case "invite" -> "You're invited to join " + model.getOrDefault("centreName", "your centre") + " on Learnium";
            case "reset" -> "Reset your Learnium password";
            default -> "Learnium notification";
        };
    }

    private Map<String, Object> toModel(JsonNode json) {
        Map<String, Object> model = new HashMap<>();
        json.fields()
                .forEachRemaining(
                        entry -> {
                            JsonNode value = entry.getValue();
                            if (value.isNull()) {
                                model.put(entry.getKey(), "");
                            } else if (value.isTextual()) {
                                model.put(entry.getKey(), value.asText());
                            } else {
                                model.put(entry.getKey(), value.toString());
                            }
                        });
        return model;
    }
}
