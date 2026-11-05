package com.learnium.repository.user;

import com.learnium.model.user.Batch;
import com.learnium.model.user.BatchMembership;
import com.learnium.model.user.MemberRole;
import com.learnium.model.user.MembershipStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BatchMembershipRepository extends JpaRepository<BatchMembership, UUID> {

    /** True when the teacher actively teaches at least one batch the student actively belongs to. */
    @Query(
            "select count(m) > 0 from BatchMembership m, BatchMembership s"
                    + " where m.centreId = :centreId and s.centreId = :centreId"
                    + " and m.batch = s.batch"
                    + " and m.user.userId = :teacherId"
                    + " and m.memberRole = com.learnium.model.user.MemberRole.TEACHER"
                    + " and m.status = com.learnium.model.user.MembershipStatus.ACTIVE"
                    + " and s.user.userId = :studentId"
                    + " and s.memberRole = com.learnium.model.user.MemberRole.STUDENT"
                    + " and s.status = com.learnium.model.user.MembershipStatus.ACTIVE")
    boolean teacherManagesStudent(
            @Param("centreId") UUID centreId,
            @Param("teacherId") UUID teacherId,
            @Param("studentId") UUID studentId);

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
     * Status-only projection for the per-request authentication check on
     * student tokens: the JWT filter verifies the roster membership behind
     * the token is still ACTIVE, loading nothing but its status.
     */
    @Query(
            "select m.status from BatchMembership m"
                    + " where m.centreId = :centreId and m.membershipId = :membershipId")
    Optional<MembershipStatus> findStatusByCentreIdAndMembershipId(
            @Param("centreId") UUID centreId, @Param("membershipId") UUID membershipId);

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
