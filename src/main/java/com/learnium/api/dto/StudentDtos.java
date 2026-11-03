package com.learnium.api.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class StudentDtos {
    private StudentDtos() {}
    public record StudentTestDto(UUID instanceId, String definitionName, String kind, String batchName,
            Instant opensAt, Instant closesAt, int maxAttempts, int attemptsUsed,
            UUID latestAttemptId, String latestAttemptStatus, String state, BigDecimal totalSoFar, boolean markingPending) {}
    public record AttemptItemDto(UUID itemId, int order, String type, BigDecimal marks, JsonNode payload) {}
    public record SavedAnswerDto(UUID itemId, JsonNode answer, Instant savedAt) {}
    public record AttemptDto(UUID attemptId, UUID instanceId, String definitionName, int attemptNo,
            String status, Instant startedAt, Instant deadlineAt, long remainingSeconds,
            List<AttemptItemDto> items, List<SavedAnswerDto> answers) {}
    public record SaveAnswerRequest(@NotNull JsonNode answer) {}
    public record StartAttemptRequest(String cameraCheckToken) {}
    public record SavedAnswerResponse(UUID itemId, JsonNode answer, Instant savedAt) {}
    public record ResultItemDto(UUID itemId, int order, String type, BigDecimal marks, JsonNode payload,
            JsonNode answer, String state, BigDecimal score, JsonNode solution, String feedback) {}
    public record AttemptResultDto(UUID attemptId, String status, Instant submittedAt, BigDecimal score,
            BigDecimal maximumScore, boolean markingPending, boolean answersReleased,
            List<ResultItemDto> items) {}
    public record PracticeDto(UUID instanceId, String definitionName, List<AttemptItemDto> cards) {}
}
