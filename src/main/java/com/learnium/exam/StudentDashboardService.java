package com.learnium.exam;

import com.learnium.api.dto.StudentDashboardDtos.*;
import com.learnium.repository.exam.*;
import com.learnium.repository.exam.QuestionAttemptRepository.DashboardComparisonFact;
import com.learnium.security.LearniumPrincipal;
import java.math.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Fixed-query student progress summary over final attempt facts, never lifetime counters. */
@Service
public class StudentDashboardService {
    private final QuestionAttemptRepository answers;
    private final ExamAttemptRepository attempts;
    public StudentDashboardService(QuestionAttemptRepository answers, ExamAttemptRepository attempts) {
        this.answers = answers; this.attempts = attempts;
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard(LearniumPrincipal caller) {
        var facts = answers.dashboardTopicFacts(caller.centreId(), caller.userId());
        long pending = facts.stream().mapToLong(row -> value(row.getPendingItemCount())).sum();
        List<TopicPerformanceDto> topics = facts.stream().filter(row -> value(row.getConfirmedItemCount()) > 0)
                .map(row -> topic(row.getTopicId(), row.getTopicName(), value(row.getConfirmedItemCount()),
                        decimal(row.getEarnedMarks()), decimal(row.getAvailableMarks()))).toList();
        List<TopicPerformanceDto> strong = topics.stream().filter(row -> "STRONG".equals(row.band())).toList();
        List<TopicPerformanceDto> focus = topics.stream().filter(row -> "FOCUS".equals(row.band())).toList();
        List<RecentResultDto> recent = attempts.dashboardRecent(caller.centreId(), caller.userId(), PageRequest.of(0, 5))
                .stream().map(row -> new RecentResultDto(row.getAttemptId(), row.getTestName(), row.getBatchName(),
                        row.getSubmittedAt(), decimal(row.getEarnedMarks()), decimal(row.getAvailableMarks()),
                        value(row.getPendingItemCount()) > 0)).toList();
        return new DashboardDto(topics, strong, focus, recent, comparisons(caller), pending);
    }

    private List<BatchComparisonDto> comparisons(LearniumPrincipal caller) {
        record Key(UUID batchId, UUID topicId) {}
        Map<Key, List<DashboardComparisonFact>> groups = answers.dashboardComparisonFacts(caller.centreId(), caller.userId())
                .stream().collect(Collectors.groupingBy(row -> new Key(row.getBatchId(), row.getTopicId()), LinkedHashMap::new, Collectors.toList()));
        List<BatchComparisonDto> result = new ArrayList<>();
        for (var entry : groups.entrySet()) {
            List<DashboardComparisonFact> rows = entry.getValue();
            if (rows.size() < 8) continue;
            DashboardComparisonFact own = rows.stream().filter(row -> row.getStudentId().equals(caller.userId())).findFirst().orElse(null);
            if (own == null || decimal(own.getAvailableMarks()).signum() <= 0) continue;
            BigDecimal earned = rows.stream().map(row -> decimal(row.getEarnedMarks())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal available = rows.stream().map(row -> decimal(row.getAvailableMarks())).reduce(BigDecimal.ZERO, BigDecimal::add);
            long atOrBelow = rows.stream().filter(row -> comparePercent(row, own) <= 0).count();
            DashboardComparisonFact first = rows.get(0);
            result.add(new BatchComparisonDto(first.getBatchId(), first.getBatchName(), first.getTopicId(), first.getTopicName(),
                    rows.size(), percent(earned, available), BigDecimal.valueOf(atOrBelow).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(rows.size()), 2, RoundingMode.HALF_UP)));
        }
        return List.copyOf(result);
    }

    private int comparePercent(DashboardComparisonFact left, DashboardComparisonFact right) {
        return decimal(left.getEarnedMarks()).multiply(decimal(right.getAvailableMarks()))
                .compareTo(decimal(right.getEarnedMarks()).multiply(decimal(left.getAvailableMarks())));
    }
    private TopicPerformanceDto topic(UUID id, String name, long count, BigDecimal earned, BigDecimal available) {
        BigDecimal percentage = percent(earned, available);
        String band = count < 3 ? null : percentage.compareTo(BigDecimal.valueOf(80)) >= 0 ? "STRONG"
                : percentage.compareTo(BigDecimal.valueOf(60)) >= 0 ? "DEVELOPING" : "FOCUS";
        return new TopicPerformanceDto(id, name, count, earned, available, percentage, band);
    }
    private BigDecimal percent(BigDecimal earned, BigDecimal available) {
        return available.signum() == 0 ? BigDecimal.ZERO : earned.multiply(BigDecimal.valueOf(100))
                .divide(available, 2, RoundingMode.HALF_UP);
    }
    private static long value(Long value) { return value == null ? 0 : value; }
    private static BigDecimal decimal(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
}
