package com.learnium.exam;

import static org.mockito.Mockito.*;

import com.learnium.model.exam.ExamAttemptStatus;
import com.learnium.repository.exam.ExamAttemptRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AttemptSweeperTest {
    @Test void submitsEveryDueAttemptAndDoesNothingWhenNoneAreDue() {
        ExamAttemptRepository attempts = mock(ExamAttemptRepository.class);
        StudentAttemptService service = mock(StudentAttemptService.class);
        Instant now = Instant.parse("2026-11-03T18:00:00Z");
        AttemptSweeper sweeper = new AttemptSweeper(attempts, service, Clock.fixed(now, ZoneOffset.UTC));
        when(attempts.dueAttemptIds(ExamAttemptStatus.IN_PROGRESS, now)).thenReturn(List.of());
        sweeper.sweep(); verifyNoInteractions(service);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        when(attempts.dueAttemptIds(ExamAttemptStatus.IN_PROGRESS, now)).thenReturn(List.of(first, second));
        sweeper.sweep(); verify(service).autoSubmit(first); verify(service).autoSubmit(second);
    }
}
