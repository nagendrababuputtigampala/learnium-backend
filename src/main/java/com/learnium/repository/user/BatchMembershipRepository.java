package com.learnium.repository.user;

import com.learnium.model.user.Batch;
import com.learnium.model.user.BatchMembership;
import com.learnium.model.user.MemberRole;
import com.learnium.model.user.MembershipStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BatchMembershipRepository extends JpaRepository<BatchMembership, UUID> {

    /** Centre-scoped roster read: the centre predicate is mandatory, not caller discipline. */
    @Query(
            "select m from BatchMembership m"
                    + " where m.centreId = :centreId"
                    + " and m.batch.batchId = :batchId and m.status = :status")
    List<BatchMembership> findByCentreIdAndBatchIdAndStatus(
            @Param("centreId") UUID centreId,
            @Param("batchId") UUID batchId,
            @Param("status") MembershipStatus status);

    @Query(
            "select case when count(m) > 0 then true else false end from BatchMembership m"
                    + " where m.centreId = :centreId"
                    + " and m.batch.batchId = :batchId and m.user.userId = :userId")
    boolean existsByCentreIdAndBatchIdAndUserId(
            @Param("centreId") UUID centreId,
            @Param("batchId") UUID batchId,
            @Param("userId") UUID userId);

    /** Role- and status-aware assignment check used by the batch access guard. */
    @Query(
            "select case when count(m) > 0 then true else false end from BatchMembership m"
                    + " where m.centreId = :centreId"
                    + " and m.batch.batchId = :batchId and m.user.userId = :userId"
                    + " and m.memberRole = :memberRole and m.status = :status")
    boolean existsByCentreIdAndBatchIdAndUserIdAndMemberRoleAndStatus(
            @Param("centreId") UUID centreId,
            @Param("batchId") UUID batchId,
            @Param("userId") UUID userId,
            @Param("memberRole") MemberRole memberRole,
            @Param("status") MembershipStatus status);

    /** One user's membership in one batch, whatever its role (assignment flows). */
    @Query(
            "select m from BatchMembership m"
                    + " where m.centreId = :centreId"
                    + " and m.batch.batchId = :batchId and m.user.userId = :userId")
    Optional<BatchMembership> findByCentreIdAndBatchIdAndUserId(
            @Param("centreId") UUID centreId,
            @Param("batchId") UUID batchId,
            @Param("userId") UUID userId);

    /** Roster slice by role (teacher assignment display, student lists). */
    @Query(
            "select m from BatchMembership m"
                    + " where m.centreId = :centreId"
                    + " and m.batch.batchId = :batchId"
                    + " and m.memberRole = :memberRole and m.status = :status"
                    + " order by m.createdAt asc")
    List<BatchMembership> findByCentreIdAndBatchIdAndMemberRoleAndStatus(
            @Param("centreId") UUID centreId,
            @Param("batchId") UUID batchId,
            @Param("memberRole") MemberRole memberRole,
            @Param("status") MembershipStatus status);

    /** Full roster slice by role, any status (the roster screen shows INACTIVE rows too). */
    @Query(
            "select m from BatchMembership m"
                    + " where m.centreId = :centreId"
                    + " and m.batch.batchId = :batchId"
                    + " and m.memberRole = :memberRole"
                    + " order by m.createdAt asc")
    List<BatchMembership> findByCentreIdAndBatchIdAndMemberRole(
            @Param("centreId") UUID centreId,
            @Param("batchId") UUID batchId,
            @Param("memberRole") MemberRole memberRole);

    @Query(
            "select count(m) from BatchMembership m"
                    + " where m.centreId = :centreId"
                    + " and m.batch.batchId = :batchId"
                    + " and m.memberRole = :memberRole and m.status = :status")
    long countByCentreIdAndBatchIdAndMemberRoleAndStatus(
            @Param("centreId") UUID centreId,
            @Param("batchId") UUID batchId,
            @Param("memberRole") MemberRole memberRole,
            @Param("status") MembershipStatus status);

    /** Centre-scoped membership lookup for roster operations (never a bare findById). */
    @Query(
            "select m from BatchMembership m"
                    + " where m.centreId = :centreId and m.membershipId = :membershipId")
    Optional<BatchMembership> findByCentreIdAndMembershipId(
            @Param("centreId") UUID centreId, @Param("membershipId") UUID membershipId);

    /**
     * Atomically records one failed PIN attempt: the counter increments and
     * the lock flips in the same statement, decided on the POST-increment
     * value. Runs in the caller's transaction — the student-session service
     * invokes it inside an independent (REQUIRES_NEW) transaction so a failed
     * attempt is committed even though the request itself is rejected.
     */
    @Modifying
    @Query(
            "update BatchMembership m"
                    + " set m.pinFailedAttempts = m.pinFailedAttempts + 1,"
                    + " m.pinLocked = case when m.pinFailedAttempts + 1 >= :maxFailures"
                    + " then true else m.pinLocked end"
                    + " where m.membershipId = :membershipId")
    int incrementPinFailures(
            @Param("membershipId") UUID membershipId, @Param("maxFailures") int maxFailures);

    /** Batches a user serves in the given role (a teacher's batch list). */
    @Query(
            "select m.batch from BatchMembership m"
                    + " where m.centreId = :centreId and m.user.userId = :userId"
                    + " and m.memberRole = :memberRole and m.status = :status"
                    + " order by m.batch.name asc")
    List<Batch> findBatchesByCentreIdAndUserIdAndMemberRoleAndStatus(
            @Param("centreId") UUID centreId,
            @Param("userId") UUID userId,
            @Param("memberRole") MemberRole memberRole,
            @Param("status") MembershipStatus status);
}
