package com.learnium.api.dto;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response records for the M6 report endpoints. */
public final class ReportDtos {

    private ReportDtos() {}

    /** Generate (or regenerate) a student report snapshot; batchId null = all the student's batches. */
    public record GenerateReportRequest(
            UUID batchId,
            @Size(max = 2000, message = "must be at most 2000 characters") String teacherComment) {}

    public record ReportOverallDto(
            long confirmedItems,
            long pendingItems,
            BigDecimal earnedMarks,
            BigDecimal availableMarks,
            BigDecimal percentage,
            BigDecimal markingCoveragePercent) {}

    public record ReportTopicDto(
            UUID topicId,
            String topicName,
            long confirmedItems,
            long pendingItems,
            BigDecimal earnedMarks,
            BigDecimal availableMarks,
            BigDecimal percentage,
            String band) {}

    public record ReportTestDto(
            UUID attemptId,
            String testName,
            String batchName,
            Instant submittedAt,
            BigDecimal earnedMarks,
            BigDecimal availableMarks,
            BigDecimal percentage,
            long pendingItems) {}

    /** The frozen facts stored on a snapshot and returned verbatim by report reads. */
    public record StoredReportFacts(
            String studentName,
            String username,
            String batchName,
            ReportOverallDto overall,
            List<ReportTopicDto> topics,
            List<ReportTestDto> tests) {}

    public record StudentReportDto(
            UUID reportId,
            UUID studentId,
            UUID batchId,
            String teacherComment,
            UUID generatedBy,
            Instant generatedAt,
            StoredReportFacts facts) {}

    /** One topic row of a batch report; percentage/band are null until the topic has confirmed results. */
    public record BatchTopicDto(
            UUID topicId,
            String topicName,
            long confirmedItems,
            long pendingItems,
            long studentCount,
            BigDecimal earnedMarks,
            BigDecimal availableMarks,
            BigDecimal percentage,
            String band) {}

    /** Batch weak-topic report (task 29): topics weakest-first, no-data topics last. */
    public record BatchTopicReportDto(UUID batchId, String batchName, List<BatchTopicDto> topics) {}
}
