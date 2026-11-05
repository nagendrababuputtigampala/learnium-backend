package com.learnium.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.learnium.repository.exam.QuestionAttemptRepository;
import com.learnium.repository.exam.QuestionAttemptRepository.BatchTopicFactRow;
import com.learnium.repository.exam.QuestionAttemptRepository.BatchTopicStudentCount;
import com.learnium.repository.exam.QuestionAttemptRepository.DashboardTopicFact;
import com.learnium.repository.exam.QuestionAttemptRepository.ReportAttemptFact;
import com.learnium.report.ReportFactsService.BatchTopicFact;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Band boundaries, null-defensive projections and batch sort order for the M6 facts service. */
class ReportFactsServiceTest {
    private final QuestionAttemptRepository answers = mock(QuestionAttemptRepository.class);
    private final ReportFactsService service = new ReportFactsService(answers);
    private final UUID centreId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID batchId = UUID.randomUUID();

    @Test
    void bandBoundariesNeedThreeConfirmedItems() {
        List<DashboardTopicFact> rows = List.of(
                topic("Strong", 3L, "27", "30"),
                topic("Developing", 3L, "18", "30"),
                topic("Focus", 3L, "10", "30"),
                topic("TooFew", 2L, "20", "20"));
        when(answers.reportTopicFacts(centreId, studentId, null)).thenReturn(rows);
        when(answers.reportAttemptFacts(centreId, studentId, null)).thenReturn(List.of());

        var facts = service.studentFacts(centreId, studentId, null);

        assertThat(facts.topics()).extracting(topic -> topic.topicName() + ":" + topic.band())
                .containsExactly("Strong:STRONG", "Developing:DEVELOPING", "Focus:FOCUS", "TooFew:null");
        assertThat(facts.overall().confirmedItems()).isEqualTo(11);
        assertThat(facts.overall().markingCoveragePercent()).isEqualByComparingTo("100");
    }

    @Test
    void nullProjectionValuesReadAsZeroAndAttemptsMapThrough() {
        DashboardTopicFact empty = topic("Empty", null, null, null);
        when(empty.getPendingItemCount()).thenReturn(null);
        when(answers.reportTopicFacts(centreId, studentId, batchId)).thenReturn(List.of(empty));
        ReportAttemptFact attempt = mock(ReportAttemptFact.class);
        when(attempt.getAttemptId()).thenReturn(UUID.randomUUID());
        when(attempt.getTestName()).thenReturn("Quiz");
        when(attempt.getBatchName()).thenReturn("Batch");
        when(attempt.getSubmittedAt()).thenReturn(Instant.EPOCH);
        when(attempt.getEarnedMarks()).thenReturn(null);
        when(attempt.getAvailableMarks()).thenReturn(null);
        when(attempt.getPendingItemCount()).thenReturn(null);
        when(answers.reportAttemptFacts(centreId, studentId, batchId)).thenReturn(List.of(attempt));

        var facts = service.studentFacts(centreId, studentId, batchId);

        assertThat(facts.topics()).isEmpty();
        assertThat(facts.overall().earnedMarks()).isEqualByComparingTo("0");
        assertThat(facts.tests()).singleElement().satisfies(test -> {
            assertThat(test.testName()).isEqualTo("Quiz");
            assertThat(test.percentage()).isEqualByComparingTo("0");
            assertThat(test.pendingItems()).isZero();
        });
    }

    @Test
    void batchSortBreaksTiesByNameAndDefaultsStudentCountToZero() {
        List<BatchTopicFactRow> rows = List.of(
                batchRow("Beta", 3L, "15", "30"),
                batchRow("Alpha", 3L, "15", "30"),
                batchRow("NoData", 0L, null, null));
        when(answers.batchTopicFacts(centreId, batchId)).thenReturn(rows);
        BatchTopicStudentCount count = mock(BatchTopicStudentCount.class);
        UUID alphaTopicId = rows.get(1).getTopicId();
        when(count.getTopicId()).thenReturn(alphaTopicId);
        when(count.getStudentCount()).thenReturn(4L);
        when(answers.batchTopicStudentCounts(centreId, batchId)).thenReturn(List.of(count));

        var facts = service.batchTopicFacts(centreId, batchId);

        assertThat(facts).extracting(BatchTopicFact::topicName).containsExactly("Alpha", "Beta", "NoData");
        assertThat(facts.get(0).studentCount()).isEqualTo(4);
        assertThat(facts.get(1).studentCount()).isZero();
        assertThat(facts.get(0).band()).isEqualTo("FOCUS");
        assertThat(facts.get(2).percentage()).isNull();
    }

    private DashboardTopicFact topic(String name, Long confirmed, String earned, String available) {
        DashboardTopicFact fact = mock(DashboardTopicFact.class);
        when(fact.getTopicId()).thenReturn(UUID.randomUUID());
        when(fact.getTopicName()).thenReturn(name);
        when(fact.getConfirmedItemCount()).thenReturn(confirmed);
        when(fact.getEarnedMarks()).thenReturn(earned == null ? null : new BigDecimal(earned));
        when(fact.getAvailableMarks()).thenReturn(available == null ? null : new BigDecimal(available));
        when(fact.getPendingItemCount()).thenReturn(0L);
        return fact;
    }

    private BatchTopicFactRow batchRow(String name, Long confirmed, String earned, String available) {
        BatchTopicFactRow row = mock(BatchTopicFactRow.class);
        when(row.getTopicId()).thenReturn(UUID.randomUUID());
        when(row.getTopicName()).thenReturn(name);
        when(row.getConfirmedItemCount()).thenReturn(confirmed);
        when(row.getPendingItemCount()).thenReturn(0L);
        when(row.getEarnedMarks()).thenReturn(earned == null ? null : new BigDecimal(earned));
        when(row.getAvailableMarks()).thenReturn(available == null ? null : new BigDecimal(available));
        return row;
    }
}
