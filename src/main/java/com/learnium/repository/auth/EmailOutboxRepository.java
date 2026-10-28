package com.learnium.repository.auth;

import com.learnium.model.auth.EmailOutbox;
import com.learnium.model.auth.EmailOutboxStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmailOutboxRepository extends JpaRepository<EmailOutbox, UUID> {

    /** Dispatcher feed: oldest first, only rows that still deserve a send attempt. */
    @Query(
            "select e from EmailOutbox e"
                    + " where (e.status = :pending or (e.status = :failed and e.attempts < :maxAttempts))"
                    + " order by e.createdAt asc")
    List<EmailOutbox> findDispatchable(
            @Param("pending") EmailOutboxStatus pending,
            @Param("failed") EmailOutboxStatus failed,
            @Param("maxAttempts") int maxAttempts,
            Pageable pageable);

    @Query("select e from EmailOutbox e where e.centreId = :centreId order by e.createdAt asc")
    List<EmailOutbox> findByCentreId(@Param("centreId") UUID centreId);
}
