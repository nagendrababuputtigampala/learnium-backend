package com.learnium.repository.auth;

import com.learnium.model.auth.EmailOutbox;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    /**
     * Dispatcher feed (ids only): rows that still deserve a send attempt —
     * PENDING, FAILED with attempts left, or SENDING rows whose claim has
     * gone stale (the claiming pass died before recording an outcome).
     */
    @Query(
            "select e.emailOutboxId from EmailOutbox e"
                    + " where (e.status = com.learnium.model.auth.EmailOutboxStatus.PENDING"
                    + " or (e.status = com.learnium.model.auth.EmailOutboxStatus.FAILED"
                    + " and e.attempts < :maxAttempts)"
                    + " or (e.status = com.learnium.model.auth.EmailOutboxStatus.SENDING"
                    + " and e.updatedAt < :staleBefore))"
                    + " order by e.createdAt asc")
    List<UUID> findDispatchCandidateIds(
            @Param("maxAttempts") int maxAttempts,
            @Param("staleBefore") Instant staleBefore,
            Pageable pageable);

    /**
     * The atomic claim: flips a candidate to SENDING only if it is still in
     * a dispatchable state. Returns 1 for the dispatcher that won the row, 0
     * for every other — two dispatcher instances can never send the same row.
     */
    @Modifying(flushAutomatically = true)
    @Query(
            "update EmailOutbox e"
                    + " set e.status = com.learnium.model.auth.EmailOutboxStatus.SENDING,"
                    + " e.updatedAt = :now"
                    + " where e.emailOutboxId = :id"
                    + " and (e.status = com.learnium.model.auth.EmailOutboxStatus.PENDING"
                    + " or (e.status = com.learnium.model.auth.EmailOutboxStatus.FAILED"
                    + " and e.attempts < :maxAttempts)"
                    + " or (e.status = com.learnium.model.auth.EmailOutboxStatus.SENDING"
                    + " and e.updatedAt < :staleBefore))")
    int claimForSending(
            @Param("id") UUID id,
            @Param("maxAttempts") int maxAttempts,
            @Param("staleBefore") Instant staleBefore,
            @Param("now") Instant now);

    @Query("select e from EmailOutbox e where e.centreId = :centreId order by e.createdAt asc")
    List<EmailOutbox> findByCentreId(@Param("centreId") UUID centreId);
}
