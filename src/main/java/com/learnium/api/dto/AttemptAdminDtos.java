package com.learnium.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AttemptAdminDtos {
    private AttemptAdminDtos() {}

    /** One row of the teacher's per-instance attempt list. Score is null until the attempt is submitted. */
    public record InstanceAttemptDto(
            UUID attemptId,
            UUID studentId,
            String displayName,
            String username,
            int attemptNo,
            String status,
            Instant startedAt,
            Instant submittedAt,
            BigDecimal score,
            BigDecimal maxScore,
            boolean markingPending,
            Map<String, Long> flags,
            long flagTotal) {}
}
