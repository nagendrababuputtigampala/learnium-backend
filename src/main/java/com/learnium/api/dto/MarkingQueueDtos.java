package com.learnium.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class MarkingQueueDtos {
    private MarkingQueueDtos() {}

    public record QueueGroupDto(UUID instanceId, String testName, String batchName, List<QueueItemDto> items) {}
    public record QueueItemDto(UUID questionAttemptId, UUID studentId, String studentName, String testName,
            Instant submittedAt, BigDecimal marks, String suggestionState) {}
    public record MarkingDetailDto(UUID questionAttemptId, UUID instanceId, String testName, String batchName,
            UUID studentId, String studentName, Instant submittedAt, BigDecimal marks, JsonNode payload,
            JsonNode answer, JsonNode aiBreakdown, String aiFeedback, String suggestionState,
            JsonNode confirmedBreakdown, BigDecimal confirmedScore, String markerFeedback) {}
    public record CriterionScoreRequest(String criterionId, BigDecimal score) {}
    public record ConfirmRequest(List<CriterionScoreRequest> criteria, String feedback) {}
}
