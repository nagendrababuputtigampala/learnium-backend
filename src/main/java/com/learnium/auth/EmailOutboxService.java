package com.learnium.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnium.mail.PayloadCipher;
import com.learnium.model.auth.EmailOutbox;
import com.learnium.model.auth.EmailOutboxStatus;
import com.learnium.repository.auth.EmailOutboxRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes outgoing mail into the transactional outbox. {@link #enqueue} runs in
 * the CALLER's transaction: if the business action rolls back, the email is
 * never sent; if it commits, the email exists and the dispatcher will deliver
 * it with retries. The payload (which carries a raw set-password token) is
 * stored encrypted via {@link PayloadCipher}; the dispatcher decrypts it in
 * memory at send time.
 */
@Service
public class EmailOutboxService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final EmailOutboxRepository emailOutboxRepository;
    private final PayloadCipher payloadCipher;

    public EmailOutboxService(
            EmailOutboxRepository emailOutboxRepository, PayloadCipher payloadCipher) {
        this.emailOutboxRepository = emailOutboxRepository;
        this.payloadCipher = payloadCipher;
    }

    @Transactional
    public EmailOutbox enqueue(
            UUID centreId, String recipient, String template, Map<String, Object> model) {
        EmailOutbox row = new EmailOutbox();
        row.setCentreId(centreId);
        row.setRecipient(recipient);
        row.setTemplate(template);
        row.setModel(payloadCipher.encrypt(toJson(model)));
        row.setStatus(EmailOutboxStatus.PENDING);
        return emailOutboxRepository.save(row);
    }

    private JsonNode toJson(Map<String, Object> model) {
        return JSON.valueToTree(model);
    }
}
