package com.learnium.exam;

import com.learnium.model.exam.ExamAttemptStatus;
import com.learnium.repository.exam.ExamAttemptRepository;
import java.time.Clock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Idempotently auto-submits attempts whose server deadline or early-close boundary passed. */
@Component
public class AttemptSweeper {
    private final ExamAttemptRepository attempts;
    private final StudentAttemptService service;
    private final Clock clock;
    public AttemptSweeper(ExamAttemptRepository attempts, StudentAttemptService service, Clock clock) {
        this.attempts = attempts; this.service = service; this.clock = clock;
    }
    @Scheduled(fixedDelayString = "${learnium.attempts.sweep-delay-ms:5000}")
    public void sweep() {
        for (var id : attempts.dueAttemptIds(ExamAttemptStatus.IN_PROGRESS, clock.instant())) service.autoSubmit(id);
    }
}
