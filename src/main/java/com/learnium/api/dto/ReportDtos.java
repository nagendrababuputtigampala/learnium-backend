package com.learnium.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Response records for the M6 report endpoints. */
public final class ReportDtos {

    private ReportDtos() {}

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
