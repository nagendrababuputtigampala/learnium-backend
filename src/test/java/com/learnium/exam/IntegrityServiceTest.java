package com.learnium.exam;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.learnium.api.dto.IntegrityDtos.IntegrityEventRequest;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.exam.ExamAttempt;
import com.learnium.model.user.UserRole;
import com.learnium.repository.exam.ExamAttemptRepository;
import com.learnium.repository.exam.IntegrityEventRepository;
import com.learnium.security.LearniumPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IntegrityServiceTest {
    @Test
    void recordRejectsAttemptsFromAnotherCentre() {
        ExamAttemptRepository attempts = mock(ExamAttemptRepository.class);
        IntegrityEventRepository events = mock(IntegrityEventRepository.class);
        UUID attemptId = UUID.randomUUID();
        ExamAttempt foreign = mock(ExamAttempt.class);
        when(foreign.getCentreId()).thenReturn(UUID.randomUUID());
        when(attempts.findById(attemptId)).thenReturn(Optional.of(foreign));
        IntegrityService service = new IntegrityService(attempts, events);
        LearniumPrincipal caller = new LearniumPrincipal(UUID.randomUUID(), UUID.randomUUID(), UserRole.STUDENT);
        var batch = List.of(new IntegrityEventRequest("TAB_HIDDEN", Instant.parse("2026-11-03T18:01:00Z")));
        assertThatThrownBy(() -> service.record(caller, attemptId, batch))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ATT_NOT_ASSIGNED);
    }
}
