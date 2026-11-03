package com.learnium.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.learnium.repository.exam.ExamAttemptRepository;
import com.learnium.repository.exam.QuestionAttemptRepository;
import com.learnium.repository.exam.QuestionAttemptRepository.DashboardComparisonFact;
import com.learnium.repository.exam.QuestionAttemptRepository.DashboardTopicFact;
import com.learnium.security.LearniumPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

class StudentDashboardServiceTest {
    private final QuestionAttemptRepository answers = mock(QuestionAttemptRepository.class);
    private final ExamAttemptRepository attempts = mock(ExamAttemptRepository.class);
    private final StudentDashboardService service = new StudentDashboardService(answers, attempts);
    private final UUID centreId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @Test
    void defensiveProjectionValuesAndIneligibleComparisonGroupsAreHandled() {
        DashboardTopicFact empty = topic(null, null, null, null);
        DashboardTopicFact zeroAvailable = topic(3L, null, null, null);
        when(answers.dashboardTopicFacts(centreId, studentId)).thenReturn(List.of(empty, zeroAvailable));

        var noPending = recent(null, null, null);
        var pending = recent(BigDecimal.ONE, BigDecimal.TEN, 1L);
        when(attempts.dashboardRecent(eq(centreId), eq(studentId), any(Pageable.class)))
                .thenReturn(List.of(noPending, pending));

        UUID noOwnerBatch = UUID.randomUUID();
        UUID zeroBatch = UUID.randomUUID();
        UUID validBatch = UUID.randomUUID();
        List<DashboardComparisonFact> comparisons = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            comparisons.add(comparison(noOwnerBatch, UUID.randomUUID(), BigDecimal.ONE, BigDecimal.TEN));
            comparisons.add(comparison(zeroBatch, index == 0 ? studentId : UUID.randomUUID(), BigDecimal.ZERO,
                    index == 0 ? BigDecimal.ZERO : BigDecimal.TEN));
            comparisons.add(comparison(validBatch, index == 0 ? studentId : UUID.randomUUID(),
                    BigDecimal.valueOf(index == 1 ? 9 : 5), BigDecimal.TEN));
        }
        when(answers.dashboardComparisonFacts(centreId, studentId)).thenReturn(comparisons);

        var dashboard = service.dashboard(LearniumPrincipal.student(studentId, centreId));

        assertThat(dashboard.pendingItemCount()).isZero();
        assertThat(dashboard.topics()).singleElement().satisfies(topic -> {
            assertThat(topic.scorePercent()).isZero();
            assertThat(topic.band()).isEqualTo("FOCUS");
        });
        assertThat(dashboard.recentResults()).extracting(result -> result.markingPending())
                .containsExactly(false, true);
        assertThat(dashboard.comparisons()).hasSize(1);
    }

    private DashboardTopicFact topic(Long confirmed, BigDecimal earned, BigDecimal available, Long pending) {
        DashboardTopicFact fact = mock(DashboardTopicFact.class);
        when(fact.getTopicId()).thenReturn(UUID.randomUUID());
        when(fact.getTopicName()).thenReturn("Topic");
        when(fact.getConfirmedItemCount()).thenReturn(confirmed);
        when(fact.getEarnedMarks()).thenReturn(earned);
        when(fact.getAvailableMarks()).thenReturn(available);
        when(fact.getPendingItemCount()).thenReturn(pending);
        return fact;
    }

    private ExamAttemptRepository.DashboardRecentFact recent(BigDecimal earned, BigDecimal available, Long pending) {
        var fact = mock(ExamAttemptRepository.DashboardRecentFact.class);
        when(fact.getAttemptId()).thenReturn(UUID.randomUUID());
        when(fact.getTestName()).thenReturn("Test");
        when(fact.getBatchName()).thenReturn("Batch");
        when(fact.getSubmittedAt()).thenReturn(Instant.EPOCH);
        when(fact.getEarnedMarks()).thenReturn(earned);
        when(fact.getAvailableMarks()).thenReturn(available);
        when(fact.getPendingItemCount()).thenReturn(pending);
        return fact;
    }

    private DashboardComparisonFact comparison(UUID batchId, UUID owner, BigDecimal earned, BigDecimal available) {
        DashboardComparisonFact fact = mock(DashboardComparisonFact.class);
        when(fact.getBatchId()).thenReturn(batchId);
        when(fact.getBatchName()).thenReturn("Batch");
        when(fact.getTopicId()).thenReturn(batchId);
        when(fact.getTopicName()).thenReturn("Topic");
        when(fact.getStudentId()).thenReturn(owner);
        when(fact.getEarnedMarks()).thenReturn(earned);
        when(fact.getAvailableMarks()).thenReturn(available);
        return fact;
    }
}
