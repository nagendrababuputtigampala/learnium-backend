package com.learnium.repository.exam;

import com.learnium.model.exam.IntegrityEvent;
import com.learnium.model.exam.IntegrityEventType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface IntegrityEventRepository extends JpaRepository<IntegrityEvent, UUID> {

    List<IntegrityEvent> findByCentreIdAndAttemptAttemptIdOrderByOccurredAtAsc(UUID centreId, UUID attemptId);

    interface FlagCount {
        UUID getAttemptId();

        IntegrityEventType getEventType();

        long getEventCount();
    }

    @Query(
            "select e.attempt.attemptId as attemptId, e.eventType as eventType, count(e) as eventCount"
                    + " from IntegrityEvent e where e.centreId = :centreId and e.attempt.attemptId in :attemptIds"
                    + " group by e.attempt.attemptId, e.eventType")
    List<FlagCount> flagCounts(UUID centreId, List<UUID> attemptIds);
}
