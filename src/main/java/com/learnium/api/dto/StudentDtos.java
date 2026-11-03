package com.learnium.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class StudentDtos {
    private StudentDtos() {}
    public record StudentTestDto(UUID instanceId, String definitionName, String kind, String batchName,
            Instant opensAt, Instant closesAt, int maxAttempts, int attemptsUsed,
            String latestAttemptStatus, String state, BigDecimal totalSoFar, boolean markingPending) {}
}
