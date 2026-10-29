package com.learnium.repository.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.model.content.BackgroundJob;
import com.learnium.model.content.GenerationFailureKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, UUID> {

    /** One job inside its centre, with the associations the API renders. */
    @Query(
            "select j from BackgroundJob j"
                    + " join fetch j.material join fetch j.topic"
                    + " where j.id = :jobId and j.centre.centreId = :centreId")
    Optional<BackgroundJob> findByIdAndCentreId(
            @Param("jobId") UUID jobId, @Param("centreId") UUID centreId);

    /** Ids of jobs due for a claim, oldest first: pending and due, or running with a dead lease. */
    @Query(
            "select j.id from BackgroundJob j"
                    + " where (j.status = com.learnium.model.content.BackgroundJobStatus.PENDING"
                    + " and j.nextAttemptAt <= :now)"
                    + " or (j.status = com.learnium.model.content.BackgroundJobStatus.RUNNING"
                    + " and j.leaseExpiresAt < :now)"
                    + " order by j.createdAt asc")
    List<UUID> findClaimCandidateIds(@Param("now") Instant now, Pageable pageable);

    /** Daily-cap count for one teacher (the audit created_by) since a day boundary. */
    long countByCentreCentreIdAndCreatedByAndCreatedAtGreaterThanEqual(
            UUID centreId, UUID createdBy, Instant since);

    /** Daily-cap count for a whole centre since a day boundary. */
    long countByCentreCentreIdAndCreatedAtGreaterThanEqual(UUID centreId, Instant since);

    /** A claimed job with every association the worker's request needs, fetched in one query. */
    @Query(
            "select j from BackgroundJob j"
                    + " join fetch j.material m"
                    + " join fetch m.gradeSubject gs"
                    + " join fetch gs.grade"
                    + " join fetch gs.subject"
                    + " join fetch j.topic"
                    + " where j.id = :id")
    Optional<BackgroundJob> findClaimSnapshotById(@Param("id") UUID id);

    /**
     * The claim: one conditional update flips a due job to RUNNING under a
     * fresh token, bumps the claim generation, takes the lease and counts
     * the attempt. Row locking + WHERE re-evaluation mean two pollers can
     * never both win: the loser's update matches zero rows.
     */
    @Modifying
    @Query(
            "update BackgroundJob j"
                    + " set j.status = com.learnium.model.content.BackgroundJobStatus.RUNNING,"
                    + " j.claimToken = :token,"
                    + " j.claimGeneration = j.claimGeneration + 1,"
                    + " j.leaseExpiresAt = :leaseExpiresAt,"
                    + " j.attempts = j.attempts + 1,"
                    + " j.updatedAt = :now"
                    + " where j.id = :id"
                    + " and ((j.status = com.learnium.model.content.BackgroundJobStatus.PENDING"
                    + " and j.nextAttemptAt <= :now)"
                    + " or (j.status = com.learnium.model.content.BackgroundJobStatus.RUNNING"
                    + " and j.leaseExpiresAt < :now))")
    int claim(
            @Param("id") UUID id,
            @Param("token") UUID token,
            @Param("leaseExpiresAt") Instant leaseExpiresAt,
            @Param("now") Instant now);

    /**
     * Ownership-checked success transition: applies only while the job is
     * still RUNNING under exactly this claim (token + generation). Zero
     * rows means the claim was lost — the caller must commit nothing.
     */
    @Modifying
    @Query(
            "update BackgroundJob j"
                    + " set j.status = com.learnium.model.content.BackgroundJobStatus.SUCCEEDED,"
                    + " j.resultCounts = :resultCounts,"
                    + " j.usageInputTokens = :usageInputTokens,"
                    + " j.usageOutputTokens = :usageOutputTokens,"
                    + " j.usageThinkingTokens = :usageThinkingTokens,"
                    + " j.failureKind = null,"
                    + " j.lastErrorClass = null,"
                    + " j.claimToken = null,"
                    + " j.leaseExpiresAt = null,"
                    + " j.finishedAt = :now,"
                    + " j.updatedAt = :now"
                    + " where j.id = :id"
                    + " and j.status = com.learnium.model.content.BackgroundJobStatus.RUNNING"
                    + " and j.claimToken = :token"
                    + " and j.claimGeneration = :generation")
    int completeSucceeded(
            @Param("id") UUID id,
            @Param("token") UUID token,
            @Param("generation") int generation,
            @Param("resultCounts") JsonNode resultCounts,
            @Param("usageInputTokens") Long usageInputTokens,
            @Param("usageOutputTokens") Long usageOutputTokens,
            @Param("usageThinkingTokens") Long usageThinkingTokens,
            @Param("now") Instant now);

    /** Ownership-checked terminal failure; counts/usage are set only when the caller has them. */
    @Modifying
    @Query(
            "update BackgroundJob j"
                    + " set j.status = com.learnium.model.content.BackgroundJobStatus.FAILED,"
                    + " j.failureKind = :failureKind,"
                    + " j.lastErrorClass = :lastErrorClass,"
                    + " j.resultCounts = :resultCounts,"
                    + " j.usageInputTokens = :usageInputTokens,"
                    + " j.usageOutputTokens = :usageOutputTokens,"
                    + " j.usageThinkingTokens = :usageThinkingTokens,"
                    + " j.claimToken = null,"
                    + " j.leaseExpiresAt = null,"
                    + " j.finishedAt = :now,"
                    + " j.updatedAt = :now"
                    + " where j.id = :id"
                    + " and j.status = com.learnium.model.content.BackgroundJobStatus.RUNNING"
                    + " and j.claimToken = :token"
                    + " and j.claimGeneration = :generation")
    int completeFailed(
            @Param("id") UUID id,
            @Param("token") UUID token,
            @Param("generation") int generation,
            @Param("failureKind") GenerationFailureKind failureKind,
            @Param("lastErrorClass") String lastErrorClass,
            @Param("resultCounts") JsonNode resultCounts,
            @Param("usageInputTokens") Long usageInputTokens,
            @Param("usageOutputTokens") Long usageOutputTokens,
            @Param("usageThinkingTokens") Long usageThinkingTokens,
            @Param("now") Instant now);

    /** Ownership-checked return to PENDING for a retryable failure, with backoff. */
    @Modifying
    @Query(
            "update BackgroundJob j"
                    + " set j.status = com.learnium.model.content.BackgroundJobStatus.PENDING,"
                    + " j.failureKind = :failureKind,"
                    + " j.lastErrorClass = :lastErrorClass,"
                    + " j.claimToken = null,"
                    + " j.leaseExpiresAt = null,"
                    + " j.nextAttemptAt = :nextAttemptAt,"
                    + " j.updatedAt = :now"
                    + " where j.id = :id"
                    + " and j.status = com.learnium.model.content.BackgroundJobStatus.RUNNING"
                    + " and j.claimToken = :token"
                    + " and j.claimGeneration = :generation")
    int reschedule(
            @Param("id") UUID id,
            @Param("token") UUID token,
            @Param("generation") int generation,
            @Param("failureKind") GenerationFailureKind failureKind,
            @Param("lastErrorClass") String lastErrorClass,
            @Param("nextAttemptAt") Instant nextAttemptAt,
            @Param("now") Instant now);
}
