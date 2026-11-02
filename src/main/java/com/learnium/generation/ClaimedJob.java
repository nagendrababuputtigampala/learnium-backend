package com.learnium.generation;

import com.learnium.model.content.GenerationDifficulty;
import java.util.Map;
import java.util.UUID;

/**
 * A claimed job as an immutable snapshot: the claim's token + generation
 * (the worker's proof of ownership for every completion), the post-claim
 * attempt count, and every frozen input the execution needs. Snapshotted
 * inside the claim transaction so the worker never touches a detached
 * entity or a lazy association.
 */
public record ClaimedJob(
        UUID jobId,
        UUID claimToken,
        int claimGeneration,
        int attempts,
        UUID centreId,
        UUID createdBy,
        UUID materialId,
        String contentHash,
        UUID topicId,
        String topicName,
        String gradeName,
        String subjectName,
        Map<GeneratedQuestionType, Integer> counts,
        GenerationDifficulty difficulty,
        String provider,
        String model,
        String promptVersion,
        String payloadSchemaVersion) {}
