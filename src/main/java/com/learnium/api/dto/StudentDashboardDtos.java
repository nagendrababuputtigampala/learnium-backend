package com.learnium.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class StudentDashboardDtos {
    private StudentDashboardDtos() {}

    public record TopicPerformanceDto(UUID topicId, String topicName, long confirmedItemCount,
            BigDecimal earnedMarks, BigDecimal availableMarks, BigDecimal scorePercent, String band) {}
    public record RecentResultDto(UUID attemptId, String testName, String batchName, Instant submittedAt,
            BigDecimal earnedMarks, BigDecimal availableMarks, boolean markingPending) {}
    public record BatchComparisonDto(UUID batchId, String batchName, UUID topicId, String topicName,
            int studentCount, BigDecimal batchAveragePercent, BigDecimal percentile) {}
    public record DashboardDto(List<TopicPerformanceDto> topics, List<TopicPerformanceDto> strongAreas,
            List<TopicPerformanceDto> areasToImprove, List<RecentResultDto> recentResults,
            List<BatchComparisonDto> comparisons, long pendingItemCount) {}
}
