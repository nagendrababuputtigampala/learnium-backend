package com.learnium.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public final class TestDtos {
    private TestDtos() {}
    public record ItemRequest(UUID questionId, BigDecimal marks) {}
    public record DefinitionRequest(String name, UUID gradeSubjectId, String kind, List<ItemRequest> items) {}
    public record PublishRequest(UUID batchId, Instant opensAt, Instant closesAt, Integer timeLimitSeconds,
            Integer maxAttempts, String answerReleasePolicy, Boolean integrityEnabled, String cameraMode,
            Map<UUID, BigDecimal> itemMarks, List<ItemRequest> items) {}
    public record StudentRequest(UUID userId) {}
    public record ItemDto(UUID questionId, String type, String topicName, BigDecimal marks, int displayOrder,
            JsonNode payload, String correctRef) {}
    public record DefinitionDto(UUID id, String name, UUID gradeSubjectId, String gradeName, String subjectName,
            String kind, UUID createdBy, int questionCount, BigDecimal totalMarks, long publishedInstanceCount,
            List<ItemDto> items) {}
    public record AssignedStudent(UUID userId, String name, UUID addedBy, Instant addedAt) {}
    public record InstanceDto(UUID id, UUID testId, String name, String kind, UUID batchId, String batchName,
            String joinCode, String status, Instant opensAt, Instant closesAt, Instant closedAt,
            Integer timeLimitSeconds, int maxAttempts, String answerReleasePolicy, boolean integrityEnabled,
            String cameraMode, List<ItemDto> items, List<AssignedStudent> students) {}
}
