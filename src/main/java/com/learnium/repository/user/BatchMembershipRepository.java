package com.learnium.repository.user;

import com.learnium.model.user.BatchMembership;
import com.learnium.model.user.MembershipStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BatchMembershipRepository extends JpaRepository<BatchMembership, UUID> {

    @Query(
            "select m from BatchMembership m"
                    + " where m.batch.batchId = :batchId and m.status = :status")
    List<BatchMembership> findByBatchIdAndStatus(
            @Param("batchId") UUID batchId, @Param("status") MembershipStatus status);

    @Query(
            "select case when count(m) > 0 then true else false end from BatchMembership m"
                    + " where m.batch.batchId = :batchId and m.user.userId = :userId")
    boolean existsByBatchIdAndUserId(@Param("batchId") UUID batchId, @Param("userId") UUID userId);
}
