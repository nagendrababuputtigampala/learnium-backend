package com.learnium.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** Generation API payloads (M3 branch 2): jobs in, job state and draft previews out. */
public final class GenerationDtos {

    private GenerationDtos() {}

    /**
     * Create-job request. {@code counts} keys are question-type codes
     * (MCQ, FILL_BLANK, FLASHCARD, NUMERIC, WRITTEN); a missing type means
     * zero. {@code difficulty} is EASY, MEDIUM or HARD; absent means MEDIUM.
     * Validation lives in the service so failures carry LRN-GEN-* codes.
     */
    public record GenerateRequest(UUID topicId, Map<String, Integer> counts, String difficulty) {}

    /** Per-type tallies of one executed job, as persisted in result_counts. */
    public record TypeResultDto(
            int requested,
            int received,
            int accepted,
            int dropped,
            Map<String, Integer> dropReasons) {}

    /** The 202 response: the frozen job exactly as created. */
    public record GenerationJobDto(
            UUID id,
            UUID materialId,
            UUID topicId,
            String status,
            Map<String, Integer> requestedCounts,
            String difficulty,
            String provider,
            String model,
            int attempts,
            Instant createdAt) {}

    /** Full job state for polling: outcome, tallies, usage and timestamps. */
    public record GenerationJobDetailDto(
            UUID id,
            UUID materialId,
            UUID topicId,
            String status,
            Map<String, Integer> requestedCounts,
            String difficulty,
            String provider,
            String model,
            String promptVersion,
            String payloadSchemaVersion,
            String contentHash,
            String failureKind,
            Map<String, TypeResultDto> resultCounts,
            Long usageInputTokens,
            Long usageOutputTokens,
            Long usageThinkingTokens,
            int attempts,
            Instant createdAt,
            Instant updatedAt,
            Instant finishedAt) {}

    /** One generated draft in a material's preview list (payload is display-ready). */
    public record GeneratedQuestionDto(
            UUID questionId,
            String type,
            UUID topicId,
            JsonNode payload,
            String correctRef,
            String provider,
            String model,
            String promptVersion,
            UUID generationJobId,
            Instant createdAt) {}
}
