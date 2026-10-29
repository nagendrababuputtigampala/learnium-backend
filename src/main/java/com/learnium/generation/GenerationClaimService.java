package com.learnium.generation;

import com.learnium.generation.GenerationOutcome.Success;
import com.learnium.model.content.BackgroundJob;
import com.learnium.model.content.GenerationFailureKind;
import com.learnium.repository.content.BackgroundJobRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The claim-ownership protocol, in one place. Claiming is a single
 * conditional update (see {@link BackgroundJobRepository#claim}); every
 * completion is another conditional update keyed on the claim's token +
 * generation. A worker that loses its lease therefore cannot finish,
 * fail or reschedule a job somebody else now owns — its updates simply
 * match zero rows, and anything it already wrote in the same
 * transaction rolls back with them.
 */
@Service
public class GenerationClaimService {

    private final BackgroundJobRepository jobRepository;
    private final GenerationProperties properties;
    private final Clock clock;

    public GenerationClaimService(
            BackgroundJobRepository jobRepository, GenerationProperties properties, Clock clock) {
        this.jobRepository = jobRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /** Claims the oldest due job, if any. Empty when nothing is due or the race was lost. */
    @Transactional
    public Optional<ClaimedJob> claimNext() {
        Instant now = clock.instant();
        var candidates = jobRepository.findClaimCandidateIds(now, PageRequest.of(0, 1));
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        UUID jobId = candidates.get(0);
        UUID token = UUID.randomUUID();
        Instant leaseExpiresAt = now.plus(Duration.ofSeconds(properties.getLeaseDurationSeconds()));
        if (jobRepository.claim(jobId, token, leaseExpiresAt, now) == 0) {
            return Optional.empty();
        }
        BackgroundJob job = jobRepository.findClaimSnapshotById(jobId).orElseThrow();
        return Optional.of(toClaimedJob(job, token));
    }

    /**
     * Returns the job to PENDING for a retryable failure, with backoff
     * (base × the attempt just spent). False when the claim was lost.
     */
    @Transactional
    public boolean reschedule(
            ClaimedJob claim, GenerationFailureKind failureKind, String lastErrorClass) {
        Instant now = clock.instant();
        Instant nextAttemptAt =
                now.plus(
                        Duration.ofSeconds(
                                properties.getRetryBackoffSeconds() * claim.attempts()));
        return jobRepository.reschedule(
                        claim.jobId(),
                        claim.claimToken(),
                        claim.claimGeneration(),
                        failureKind,
                        lastErrorClass,
                        nextAttemptAt,
                        now)
                == 1;
    }

    /**
     * Marks the job terminally FAILED. Counts and usage are recorded
     * when the execution produced them (e.g. ZERO_VALID); otherwise the
     * columns stay null. False when the claim was lost.
     */
    @Transactional
    public boolean failTerminal(
            ClaimedJob claim,
            GenerationFailureKind failureKind,
            String lastErrorClass,
            GenerationResultCounts counts,
            Success usage) {
        return jobRepository.completeFailed(
                        claim.jobId(),
                        claim.claimToken(),
                        claim.claimGeneration(),
                        failureKind,
                        lastErrorClass,
                        counts == null ? null : counts.toJson(),
                        usage == null ? null : usage.usageInputTokens(),
                        usage == null ? null : usage.usageOutputTokens(),
                        usage == null ? null : usage.usageThinkingTokens(),
                        clock.instant())
                == 1;
    }

    private ClaimedJob toClaimedJob(BackgroundJob job, UUID token) {
        Map<String, Integer> rawCounts = GenerationJobService.countsFromJson(job.getRequestedCounts());
        EnumMap<GeneratedQuestionType, Integer> counts = new EnumMap<>(GeneratedQuestionType.class);
        rawCounts.forEach((code, count) -> counts.put(GeneratedQuestionType.valueOf(code), count));
        return new ClaimedJob(
                job.getId(),
                token,
                job.getClaimGeneration(),
                job.getAttempts(),
                job.getCentre().getCentreId(),
                job.getCreatedBy(),
                job.getMaterial().getMaterialId(),
                job.getContentHash(),
                job.getTopic().getTopicId(),
                job.getTopic().getTopicName(),
                job.getMaterial().getGradeSubject().getGrade().getName(),
                job.getMaterial().getGradeSubject().getSubject().getSubjectName(),
                counts,
                job.getDifficulty(),
                job.getProvider(),
                job.getModel(),
                job.getPromptVersion(),
                job.getPayloadSchemaVersion());
    }
}
