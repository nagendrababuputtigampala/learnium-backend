package com.learnium.model.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.model.common.AuditableEntity;
import com.learnium.model.user.Centre;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One AI generation job: turn a material's extracted text into draft
 * questions on one topic. Everything the worker needs is frozen onto the
 * row at creation — material, the material's content hash, topic,
 * per-type counts, difficulty, provider/model and the prompt + payload
 * schema versions — so configuration changes never alter a queued job.
 *
 * <p>Claiming is ownership-based: a worker stamps a fresh claim token,
 * bumps the claim generation and takes a lease, in one conditional
 * update. Every completion (success, failure, reschedule) is another
 * conditional update on (status RUNNING, claim token, claim generation),
 * so a worker whose lease expired and was reclaimed commits nothing.
 * Attempts are counted at claim time; at most two claims ever execute a
 * job. The audit {@code created_by} is the requesting teacher, set
 * explicitly at creation (the worker runs without a security context).
 */
@Entity
@Table(
        name = "background_job",
        schema = "learnium",
        indexes = {
            @Index(name = "idx_background_job_due", columnList = "status, next_attempt_at"),
            @Index(name = "idx_background_job_material", columnList = "material_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class BackgroundJob extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "centre_id", nullable = false)
    private Centre centre;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "material_id", nullable = false)
    private Material material;

    /** SHA-256 of the material's complete extracted text, frozen at creation. */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    /** Per-type requested counts, keyed by question-type code. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "requested_counts", nullable = false, columnDefinition = "jsonb")
    private JsonNode requestedCounts;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", nullable = false, length = 16)
    private GenerationDifficulty difficulty;

    @Column(name = "provider", nullable = false, length = 16)
    private String provider;

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 32)
    private String promptVersion;

    @Column(name = "payload_schema_version", nullable = false, length = 32)
    private String payloadSchemaVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BackgroundJobStatus status = BackgroundJobStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_kind", length = 32)
    private GenerationFailureKind failureKind;

    /** Claims so far; incremented by the claiming update, never by hand. */
    @Column(name = "attempts", nullable = false)
    private int attempts = 0;

    /** Token of the current claim; null while the job is not claimed. */
    @Column(name = "claim_token")
    private UUID claimToken;

    /** Monotonic claim counter; a stale claim's generation never matches again. */
    @Column(name = "claim_generation", nullable = false)
    private int claimGeneration = 0;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    /** Earliest time a PENDING job may be claimed (creation time, or a retry backoff). */
    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    /** Per-type requested/received/accepted/dropped tallies, once executed. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_counts", columnDefinition = "jsonb")
    private JsonNode resultCounts;

    @Column(name = "usage_input_tokens")
    private Long usageInputTokens;

    @Column(name = "usage_output_tokens")
    private Long usageOutputTokens;

    @Column(name = "usage_thinking_tokens")
    private Long usageThinkingTokens;

    /** Exception class of the last failure — never a message, which could carry content. */
    @Column(name = "last_error_class", length = 128)
    private String lastErrorClass;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
