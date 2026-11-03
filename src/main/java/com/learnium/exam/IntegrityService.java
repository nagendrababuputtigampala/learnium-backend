package com.learnium.exam;

import com.learnium.api.dto.IntegrityDtos.IntegrityEventRequest;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.exam.ExamAttempt;
import com.learnium.model.exam.ExamAttemptStatus;
import com.learnium.model.exam.IntegrityEvent;
import com.learnium.model.exam.IntegrityEventType;
import com.learnium.repository.exam.ExamAttemptRepository;
import com.learnium.repository.exam.IntegrityEventRepository;
import com.learnium.security.LearniumPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Student-side integrity signal ingestion. Events are flags for the teacher —
 * they never fail, submit, or otherwise change the attempt. When the instance
 * has signals OFF (or the attempt is already invalidated) the batch is
 * accepted and dropped silently, so the player needs no special casing.
 */
@Service
public class IntegrityService {
    static final int MAX_BATCH = 20;

    private final ExamAttemptRepository attempts;
    private final IntegrityEventRepository events;

    public IntegrityService(ExamAttemptRepository attempts, IntegrityEventRepository events) {
        this.attempts = attempts;
        this.events = events;
    }

    @Transactional
    public int record(LearniumPrincipal caller, UUID attemptId, List<IntegrityEventRequest> batch) {
        ExamAttempt attempt =
                attempts.findById(attemptId)
                        .filter(
                                row ->
                                        row.getCentreId().equals(caller.centreId())
                                                && row.getUser().getUserId().equals(caller.userId()))
                        .orElseThrow(() -> new BusinessException(ErrorCode.ATT_NOT_ASSIGNED));
        if (batch == null || batch.isEmpty() || batch.size() > MAX_BATCH) throw invalid();
        List<IntegrityEventType> types = batch.stream().map(this::parse).toList();
        if (!attempt.getExamInstance().isIntegritySignalsOn()
                || attempt.getStatus() == ExamAttemptStatus.INVALIDATED) {
            return 0;
        }
        for (int i = 0; i < batch.size(); i++) {
            IntegrityEvent event = new IntegrityEvent();
            event.setCentreId(caller.centreId());
            event.setAttempt(attempt);
            event.setEventType(types.get(i));
            event.setOccurredAt(batch.get(i).occurredAt());
            events.save(event);
        }
        attempt.setIntegrityFlagCount(attempt.getIntegrityFlagCount() + batch.size());
        attempts.save(attempt);
        return batch.size();
    }

    private IntegrityEventType parse(IntegrityEventRequest request) {
        if (request == null || request.type() == null || request.occurredAt() == null) throw invalid();
        try {
            return IntegrityEventType.valueOf(request.type());
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
    }

    private BusinessException invalid() {
        return new BusinessException(ErrorCode.VAL_FAILED);
    }
}
