package com.learnium.repository.content;

import com.learnium.model.content.BackgroundJob;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BackgroundJobRepository extends JpaRepository<BackgroundJob, UUID> {

    /** One job inside its centre, with the associations the API renders. */
    @Query(
            "select j from BackgroundJob j"
                    + " join fetch j.material join fetch j.topic"
                    + " where j.id = :jobId and j.centre.centreId = :centreId")
    Optional<BackgroundJob> findByIdAndCentreId(
            @Param("jobId") UUID jobId, @Param("centreId") UUID centreId);

    /** Ids of jobs due for a claim, oldest first: pending and due, or running with a dead lease. */
    @Query(
            "select j.id from BackgroundJob j"
                    + " where (j.status = com.learnium.model.content.BackgroundJobStatus.PENDING"
                    + " and j.nextAttemptAt <= :now)"
                    + " or (j.status = com.learnium.model.content.BackgroundJobStatus.RUNNING"
                    + " and j.leaseExpiresAt < :now)"
                    + " order by j.createdAt asc")
    List<UUID> findClaimCandidateIds(@Param("now") Instant now, Pageable pageable);

    /** Daily-cap count for one teacher (the audit created_by) since a day boundary. */
    long countByCentreCentreIdAndCreatedByAndCreatedAtGreaterThanEqual(
            UUID centreId, UUID createdBy, Instant since);

    /** Daily-cap count for a whole centre since a day boundary. */
    long countByCentreCentreIdAndCreatedAtGreaterThanEqual(UUID centreId, Instant since);
}
