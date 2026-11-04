package com.learnium.report;

import com.learnium.repository.exam.QuestionAttemptRepository;
import com.learnium.repository.exam.QuestionAttemptRepository.BatchTopicFactRow;
import com.learnium.repository.exam.QuestionAttemptRepository.BatchTopicStudentCount;
import com.learnium.repository.exam.QuestionAttemptRepository.DashboardTopicFact;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * M6 report facts, computed only from attempt facts (question attempts →
 * published items → instances) — never from USER_TOPIC_STAT lifetime counters.
 * Confirmed results (AUTO-graded or teacher-CONFIRMED) earn marks; items still
 * awaiting marking are excluded from both the numerator and the denominator
 * and surface separately as pending counts + marking coverage.
 */
@Service
public class ReportFactsService {

    private final QuestionAttemptRepository answers;

    public ReportFactsService(QuestionAttemptRepository answers) {
        this.answers = answers;
    }

    public record TopicFact(
            UUID topicId,
            String topicName,
            long confirmedItems,
            long pendingItems,
            BigDecimal earnedMarks,
            BigDecimal availableMarks,
            BigDecimal percentage,
            String band) {}

    public record AttemptFact(
            UUID attemptId,
            String testName,
            String batchName,
            Instant submittedAt,
            BigDecimal earnedMarks,
            BigDecimal availableMarks,
            BigDecimal percentage,
            long pendingItems) {}

    public record OverallFact(
            long confirmedItems,
            long pendingItems,
            BigDecimal earnedMarks,
            BigDecimal availableMarks,
            BigDecimal percentage,
            BigDecimal markingCoveragePercent) {}

    public record StudentFacts(OverallFact overall, List<TopicFact> topics, List<AttemptFact> tests) {}

    public record BatchTopicFact(
            UUID topicId,
            String topicName,
            long confirmedItems,
            long pendingItems,
            long studentCount,
            BigDecimal earnedMarks,
            BigDecimal availableMarks,
            BigDecimal percentage,
            String band) {}

    /** Facts for one student; {@code batchId} null scopes to all the student's batches. */
    @Transactional(readOnly = true)
    public StudentFacts studentFacts(UUID centreId, UUID studentId, UUID batchId) {
        List<DashboardTopicFact> rows = answers.reportTopicFacts(centreId, studentId, batchId);
        long confirmed = 0;
        long pending = 0;
        BigDecimal earned = BigDecimal.ZERO;
        BigDecimal available = BigDecimal.ZERO;
        List<TopicFact> topics = new java.util.ArrayList<>();
        for (DashboardTopicFact row : rows) {
            long rowConfirmed = value(row.getConfirmedItemCount());
            long rowPending = value(row.getPendingItemCount());
            BigDecimal rowEarned = decimal(row.getEarnedMarks());
            BigDecimal rowAvailable = decimal(row.getAvailableMarks());
            confirmed += rowConfirmed;
            pending += rowPending;
            earned = earned.add(rowEarned);
            available = available.add(rowAvailable);
            if (rowConfirmed > 0) {
                BigDecimal percentage = percent(rowEarned, rowAvailable);
                topics.add(new TopicFact(
                        row.getTopicId(),
                        row.getTopicName(),
                        rowConfirmed,
                        rowPending,
                        rowEarned,
                        rowAvailable,
                        percentage,
                        band(rowConfirmed, percentage)));
            }
        }
        OverallFact overall = new OverallFact(
                confirmed, pending, earned, available, percent(earned, available), coverage(confirmed, pending));
        List<AttemptFact> tests = answers.reportAttemptFacts(centreId, studentId, batchId).stream()
                .map(row -> new AttemptFact(
                        row.getAttemptId(),
                        row.getTestName(),
                        row.getBatchName(),
                        row.getSubmittedAt(),
                        decimal(row.getEarnedMarks()),
                        decimal(row.getAvailableMarks()),
                        percent(decimal(row.getEarnedMarks()), decimal(row.getAvailableMarks())),
                        value(row.getPendingItemCount())))
                .toList();
        return new StudentFacts(overall, List.copyOf(topics), tests);
    }

    /** Per-topic aggregates for one batch, weakest first; topics without confirmed data go last. */
    @Transactional(readOnly = true)
    public List<BatchTopicFact> batchTopicFacts(UUID centreId, UUID batchId) {
        Map<UUID, Long> studentsByTopic = answers.batchTopicStudentCounts(centreId, batchId).stream()
                .collect(Collectors.toMap(
                        BatchTopicStudentCount::getTopicId, row -> value(row.getStudentCount())));
        List<BatchTopicFact> facts = answers.batchTopicFacts(centreId, batchId).stream()
                .map(row -> batchFact(row, studentsByTopic.getOrDefault(row.getTopicId(), 0L)))
                .collect(Collectors.toCollection(java.util.ArrayList::new));
        facts.sort(Comparator.comparing(
                        (BatchTopicFact fact) -> fact.percentage() == null)
                .thenComparing(BatchTopicFact::percentage, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(BatchTopicFact::topicName, Comparator.nullsLast(Comparator.naturalOrder())));
        return List.copyOf(facts);
    }

    private BatchTopicFact batchFact(BatchTopicFactRow row, long studentCount) {
        long confirmed = value(row.getConfirmedItemCount());
        BigDecimal earned = decimal(row.getEarnedMarks());
        BigDecimal available = decimal(row.getAvailableMarks());
        BigDecimal percentage = confirmed == 0 ? null : percent(earned, available);
        return new BatchTopicFact(
                row.getTopicId(),
                row.getTopicName(),
                confirmed,
                value(row.getPendingItemCount()),
                studentCount,
                earned,
                available,
                percentage,
                percentage == null ? null : band(confirmed, percentage));
    }

    /** Same banding as the student dashboard: a band needs at least 3 confirmed items. */
    static String band(long confirmedItems, BigDecimal percentage) {
        if (confirmedItems < 3) {
            return null;
        }
        if (percentage.compareTo(BigDecimal.valueOf(80)) >= 0) {
            return "STRONG";
        }
        if (percentage.compareTo(BigDecimal.valueOf(60)) >= 0) {
            return "DEVELOPING";
        }
        return "FOCUS";
    }

    static BigDecimal percent(BigDecimal earned, BigDecimal available) {
        return available.signum() == 0
                ? BigDecimal.ZERO
                : earned.multiply(BigDecimal.valueOf(100)).divide(available, 2, RoundingMode.HALF_UP);
    }

    /** Share of items whose marking is final; nothing outstanding reads as full coverage. */
    static BigDecimal coverage(long confirmed, long pending) {
        long total = confirmed + pending;
        if (total == 0) {
            return BigDecimal.valueOf(100);
        }
        return BigDecimal.valueOf(confirmed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private static long value(Long value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
