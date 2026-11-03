package com.learnium.model.exam;

import com.learnium.model.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One flagged moment during an attempt (tab hidden, focus lost, camera lost, …). */
@Entity
@Table(
        name = "integrity_events",
        schema = "learnium",
        indexes = {
            @Index(name = "idx_integrity_events_attempt", columnList = "attempt_id, occurred_at"),
            @Index(name = "idx_integrity_events_centre", columnList = "centre_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class IntegrityEvent extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "centre_id", nullable = false)
    private UUID centreId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private ExamAttempt attempt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private IntegrityEventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;
}
