package com.learnium.generation;

import com.learnium.generation.DraftQuestionFactory.ValidationOutcome;
import com.learnium.generation.GenerationOutcome.Failure;
import com.learnium.generation.GenerationOutcome.Success;
import com.learnium.generation.GenerationPersistenceService.PersistResult;
import com.learnium.model.content.GenerationFailureKind;
import com.learnium.model.content.Material;
import com.learnium.repository.content.MaterialRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Executes generation jobs, one per {@link #runOnce()} call: claim in a
 * short transaction, call the model OUTSIDE any transaction, then commit
 * (or fail / reschedule) through ownership-checked transitions. At most
 * {@value #MAX_ATTEMPTS} claims ever execute a job; transient failure
 * kinds (model error, malformed response, truncation) retry with
 * backoff, the rest fail terminally on the spot.
 *
 * <p>Log lines carry ids, kinds and counts only — never source text,
 * prompts or model output.
 */
@Component
public class GenerationWorker {

    static final int MAX_ATTEMPTS = 2;

    private static final Logger log = LoggerFactory.getLogger(GenerationWorker.class);

    private final GenerationClaimService claimService;
    private final MaterialRepository materialRepository;
    private final QuestionGenerator generator;
    private final DraftQuestionFactory draftFactory = new DraftQuestionFactory();
    private final GenerationPersistenceService persistenceService;

    public GenerationWorker(
            GenerationClaimService claimService,
            MaterialRepository materialRepository,
            QuestionGenerator generator,
            GenerationPersistenceService persistenceService) {
        this.claimService = claimService;
        this.materialRepository = materialRepository;
        this.generator = generator;
        this.persistenceService = persistenceService;
    }

    /** Processes one due job. Returns false when no job was due. */
    public boolean runOnce() {
        Optional<ClaimedJob> claimed = claimService.claimNext();
        if (claimed.isEmpty()) {
            return false;
        }
        ClaimedJob claim = claimed.get();
        Material material = materialRepository.findById(claim.materialId()).orElse(null);
        if (material == null
                || material.getDeletedAt() != null
                || material.getExtractedText() == null
                || material.getExtractedText().isBlank()) {
            // The source is gone (or was never usable): nothing to call
            // the model with, and nothing may be committed.
            claimService.failTerminal(claim, GenerationFailureKind.MATERIAL_DELETED, null, null, null);
            log.info("Generation job {} failed: source material unavailable", claim.jobId());
            return true;
        }
        GenerationOutcome outcome;
        try {
            outcome =
                    generator.generate(
                            new GenerationRequest(
                                    material.getExtractedText(),
                                    claim.topicName(),
                                    claim.gradeName(),
                                    claim.subjectName(),
                                    claim.counts(),
                                    claim.difficulty(),
                                    claim.payloadSchemaVersion()));
        } catch (RuntimeException e) {
            // Adapters classify their own failures; anything escaping is
            // a model error by definition here.
            outcome = new Failure(GenerationFailureKind.MODEL_ERROR, e.getClass().getSimpleName());
        }
        if (outcome instanceof Success success) {
            onSuccess(claim, success);
        } else if (outcome instanceof Failure failure) {
            onFailure(claim, failure);
        }
        return true;
    }

    private void onSuccess(ClaimedJob claim, Success success) {
        ValidationOutcome validation = draftFactory.validate(success.questions(), claim.counts());
        if (validation.drafts().isEmpty()) {
            claimService.failTerminal(
                    claim, GenerationFailureKind.ZERO_VALID, null, validation.counts(), success);
            log.info(
                    "Generation job {} failed: zero valid questions from {} received",
                    claim.jobId(),
                    validation.counts().byType().values().stream()
                            .mapToInt(GenerationResultCounts.TypeResult::received)
                            .sum());
            return;
        }
        PersistResult result;
        try {
            result = persistenceService.persistSuccess(claim, validation, success);
        } catch (RuntimeException e) {
            // Crash path: the transaction rolled back and the job stays
            // RUNNING; its lease will make it reclaimable. Never fail the
            // job for an infrastructure error.
            log.warn(
                    "Generation job {} could not commit ({}); it will be reclaimed after its lease",
                    claim.jobId(),
                    e.getClass().getSimpleName());
            return;
        }
        if (result == PersistResult.MATERIAL_GONE) {
            claimService.failTerminal(claim, GenerationFailureKind.MATERIAL_DELETED, null, null, null);
            log.info("Generation job {} failed: source material changed mid-flight", claim.jobId());
        } else if (result == PersistResult.COMMITTED) {
            log.info(
                    "Generation job {} succeeded: {} drafts committed",
                    claim.jobId(),
                    validation.drafts().size());
        }
    }

    private void onFailure(ClaimedJob claim, Failure failure) {
        if (isRetryable(failure.kind()) && claim.attempts() < MAX_ATTEMPTS) {
            claimService.reschedule(claim, failure.kind(), failure.errorClass());
            log.info(
                    "Generation job {} rescheduled after {} (attempt {})",
                    claim.jobId(),
                    failure.kind(),
                    claim.attempts());
        } else {
            claimService.failTerminal(claim, failure.kind(), failure.errorClass(), null, null);
            log.info(
                    "Generation job {} failed terminally: {} (attempt {})",
                    claim.jobId(),
                    failure.kind(),
                    claim.attempts());
        }
    }

    private static boolean isRetryable(GenerationFailureKind kind) {
        return kind == GenerationFailureKind.MODEL_ERROR
                || kind == GenerationFailureKind.MALFORMED_RESPONSE
                || kind == GenerationFailureKind.TRUNCATED;
    }
}
