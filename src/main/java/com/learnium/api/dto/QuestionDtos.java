package com.learnium.api.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class QuestionDtos {
    private QuestionDtos() {}

    /** Content uses the M3 generator shape; type, grade/subject and provenance are server-owned. */
    public record EditRequest(@NotNull @PositiveOrZero Long revision,
            @NotNull UUID topicId, @Min(1) @Max(5) short difficulty, @NotNull java.util.Map<String, Object> content) {}

    public record DecisionRequest(@NotNull @PositiveOrZero Long revision) {}

    public record QuestionDto(UUID questionId, String type, String status, UUID gradeSubjectId,
            String gradeName, String subjectName, UUID topicId, String topicName, short difficulty,
            JsonNode payload, String correctRef, long revision, int version, UUID createdBy,
            Instant createdAt, UUID approvedBy, Instant approvedAt, UUID sourceQuestionId,
            UUID materialId, String materialTitle, String provider, String model,
            String promptVersion, UUID generationJobId) {}
}
