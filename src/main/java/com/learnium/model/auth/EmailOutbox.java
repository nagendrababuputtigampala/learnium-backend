package com.learnium.model.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.model.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Transactional outbox row for one outgoing email. Written in the same
 * transaction as the action that caused it (invite, password reset), then
 * rendered and sent asynchronously by the dispatcher with bounded retries.
 * {@code lastError} records the exception class name only — never message
 * content, which could carry client input.
 */
@Entity
@Table(
        name = "email_outbox",
        schema = "learnium",
        indexes = {
            @Index(name = "idx_email_outbox_status", columnList = "status, created_at"),
            @Index(name = "idx_email_outbox_centre", columnList = "centre_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class EmailOutbox extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "email_outbox_id", nullable = false, updatable = false)
    private UUID emailOutboxId;

    @Column(name = "centre_id", nullable = false)
    private UUID centreId;

    @Column(name = "recipient", nullable = false, length = 255)
    private String recipient;

    /** Template key understood by the mail templates (e.g. {@code invite}, {@code reset}). */
    @Column(name = "template", nullable = false, length = 50)
    private String template;

    /** Template model (names, raw one-time token, expiry) as a JSON document. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model", nullable = false, columnDefinition = "jsonb")
    private JsonNode model;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmailOutboxStatus status = EmailOutboxStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    @Column(name = "last_error", length = 255)
    private String lastError;

    @Column(name = "sent_at")
    private Instant sentAt;
}
