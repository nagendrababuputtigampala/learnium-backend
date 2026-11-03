package com.learnium.exam;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.learnium.model.exam.ExamAttemptStatus;
import com.learnium.model.user.UserRole;
import com.learnium.repository.exam.ExamInstanceStudentRepository;
import com.learnium.security.LearniumPrincipal;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class StudentTestServiceTest {
    private final ExamInstanceStudentRepository repo = mock(ExamInstanceStudentRepository.class);
    private final Instant now = Instant.parse("2026-11-03T10:00:00Z");
    private final UUID centre = UUID.randomUUID(), user = UUID.randomUUID();
    private final LearniumPrincipal caller = LearniumPrincipal.student(user, centre);

    @Test void derivesEveryStudentHomeStateAndLatestSubmittedScore() {
        UUID upcoming = UUID.randomUUID(), closed = UUID.randomUUID(), inProgress = UUID.randomUUID(), open = UUID.randomUUID(), exhausted = UUID.randomUUID();
        var rows = List.of(
                row(upcoming, now.plusSeconds(60), now.plusSeconds(3600), null, 2, null, null, null, null, false),
                row(closed, now.minusSeconds(3600), now.plusSeconds(3600), now.minusSeconds(1), 2, 1, ExamAttemptStatus.STARTED, null, null, false),
                row(inProgress, now.minusSeconds(60), now.plusSeconds(3600), null, 1, 1, ExamAttemptStatus.STARTED, null, null, false),
                row(open, now.minusSeconds(60), now.plusSeconds(3600), null, 2, 1, ExamAttemptStatus.SUBMITTED, now.minusSeconds(5), new BigDecimal("3.25"), true),
                row(exhausted, now.minusSeconds(60), now.plusSeconds(3600), null, 2, 1, ExamAttemptStatus.SUBMITTED, now.minusSeconds(10), BigDecimal.ONE, false),
                row(exhausted, now.minusSeconds(60), now.plusSeconds(3600), null, 2, 2, ExamAttemptStatus.SUBMITTED, now.minusSeconds(5), new BigDecimal("2.00"), false));
        when(repo.studentTests(centre, user)).thenReturn(rows);
        var result = new StudentTestService(repo, Clock.fixed(now, ZoneOffset.UTC)).tests(caller);
        assertThat(result).extracting(dto -> dto.state()).containsExactly("UPCOMING", "COMPLETED", "AVAILABLE", "AVAILABLE", "COMPLETED");
        assertThat(result.get(3).totalSoFar()).isEqualByComparingTo("3.25");
        assertThat(result.get(3).markingPending()).isTrue();
        assertThat(result.get(4).attemptsUsed()).isEqualTo(2);
        assertThat(result.get(4).latestAttemptStatus()).isEqualTo("SUBMITTED");
        assertThat(result.get(1).totalSoFar()).isNull();
    }

    @Test void emptyAssignmentProjectionReturnsEmptyList() {
        when(repo.studentTests(centre, user)).thenReturn(List.of());
        assertThat(new StudentTestService(repo, Clock.fixed(now, ZoneOffset.UTC)).tests(caller)).isEmpty();
    }

    @Test void aPastCloseTimeIsClosedWithoutAnExplicitEarlyCloseTimestamp() {
        UUID id = UUID.randomUUID();
        var closed = row(id, now.minusSeconds(3600), now.minusSeconds(1), null,
                2, null, null, null, null, false);
        when(repo.studentTests(centre, user)).thenReturn(List.of(closed));
        assertThat(new StudentTestService(repo, Clock.fixed(now, ZoneOffset.UTC)).tests(caller))
                .singleElement().satisfies(test -> assertThat(test.state()).isEqualTo("COMPLETED"));
    }

    private ExamInstanceStudentRepository.StudentTestRow row(UUID id, Instant opens, Instant closes, Instant closed,
            int max, Integer no, ExamAttemptStatus status, Instant submitted, BigDecimal score, boolean pending) {
        var row = mock(ExamInstanceStudentRepository.StudentTestRow.class);
        when(row.getInstanceId()).thenReturn(id); when(row.getDefinitionName()).thenReturn("Fractions");
        when(row.getDefinitionKind()).thenReturn("SCORED_TEST"); when(row.getBatchName()).thenReturn("Batch");
        when(row.getOpensAt()).thenReturn(opens); when(row.getClosesAt()).thenReturn(closes); when(row.getClosedAt()).thenReturn(closed);
        when(row.getMaxAttempts()).thenReturn(max); when(row.getAttemptNo()).thenReturn(no); when(row.getAttemptStatus()).thenReturn(status);
        when(row.getSubmittedAt()).thenReturn(submitted); when(row.getTotalScore()).thenReturn(score); when(row.getMarkingPending()).thenReturn(pending);
        return row;
    }
}
