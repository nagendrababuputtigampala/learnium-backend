package com.learnium.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.model.auth.EmailOutbox;
import com.learnium.model.auth.EmailOutboxStatus;
import com.learnium.repository.auth.EmailOutboxRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Delivers outbox rows: renders the template, sends via {@link EmailSender},
 * and records the outcome on the row. Failures are retried on later passes
 * until {@link #MAX_ATTEMPTS} attempts; {@code lastError} stores the exception
 * class name only — never a message, which could carry recipient input.
 */
@Component
public class OutboxDispatcher {

    public static final int MAX_ATTEMPTS = 3;
    private static final int BATCH_SIZE = 20;
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

    private final EmailOutboxRepository emailOutboxRepository;
    private final MailTemplateRenderer renderer;
    private final EmailSender emailSender;
    private final String publicBaseUrl;

    public OutboxDispatcher(
            EmailOutboxRepository emailOutboxRepository,
            MailTemplateRenderer renderer,
            EmailSender emailSender,
            @Value("${learnium.app.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.renderer = renderer;
        this.emailSender = emailSender;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Scheduled(fixedDelayString = "${learnium.mail.dispatch-delay-ms:5000}")
    @Transactional
    public void dispatchPending() {
        for (EmailOutbox row :
                emailOutboxRepository.findDispatchable(
                        EmailOutboxStatus.PENDING,
                        EmailOutboxStatus.FAILED,
                        MAX_ATTEMPTS,
                        PageRequest.of(0, BATCH_SIZE))) {
            dispatchOne(row);
        }
    }

    private void dispatchOne(EmailOutbox row) {
        row.setAttempts(row.getAttempts() + 1);
        try {
            Map<String, Object> model = toModel(row.getModel());
            model.put("link", buildSetPasswordLink(model));
            String subject = subjectFor(row.getTemplate(), model);
            String html = renderer.render(row.getTemplate(), model);
            emailSender.send(new EmailMessage(row.getRecipient(), subject, html, renderer.toPlainText(html)));
            row.setStatus(EmailOutboxStatus.SENT);
            row.setSentAt(Instant.now());
            row.setLastError(null);
        } catch (RuntimeException e) {
            row.setStatus(EmailOutboxStatus.FAILED);
            row.setLastError(e.getClass().getSimpleName());
            log.warn(
                    "Outbox dispatch failed for {} (attempt {}, cause {})",
                    row.getEmailOutboxId(),
                    row.getAttempts(),
                    e.getClass().getSimpleName());
        }
        emailOutboxRepository.save(row);
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
