package com.learnium.model.exam;

import com.learnium.model.common.AuditableEntity;
import com.learnium.model.user.UserAccount;
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

/**
 * One still frame captured for a camera-checked test. The PRE_TEST row is
 * created before the attempt exists (against instance + student) and carries
 * the hashed camera-check token; it is linked to the attempt when the token
 * is consumed at start. Rows and their stored images are deleted after 30 days.
 */
@Entity
@Table(
        name = "proctor_snapshots",
        schema = "learnium",
        indexes = {
            @Index(name = "idx_proctor_snapshots_attempt", columnList = "attempt_id, captured_at"),
            @Index(name = "idx_proctor_snapshots_captured", columnList = "captured_at"),
            @Index(name = "idx_proctor_snapshots_check_token", columnList = "check_token_hash")
        })
@Getter
@Setter
@NoArgsConstructor
public class ProctorSnapshot extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "snapshot_id", nullable = false, updatable = false)
    private UUID snapshotId;

    @Column(name = "centre_id", nullable = false)
    private UUID centreId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id")
    private ExamAttempt attempt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_instance_id", nullable = false)
    private ExamInstance examInstance;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    private SnapshotReason reason;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    /** SHA-256 of the camera-check token (PRE_TEST rows only); the raw token is never stored. */
    @Column(name = "check_token_hash", length = 64)
    private String checkTokenHash;

    @Column(name = "check_expires_at")
    private Instant checkExpiresAt;

    @Column(name = "check_used_at")
    private Instant checkUsedAt;
}
