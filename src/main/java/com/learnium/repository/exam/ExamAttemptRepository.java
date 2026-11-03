package com.learnium.repository.exam;

import com.learnium.model.exam.ExamAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;
import com.learnium.model.exam.ExamAttemptStatus;
import java.math.BigDecimal;
import org.springframework.data.domain.Pageable;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, UUID> {

    interface DashboardRecentFact {
        UUID getAttemptId();
        String getTestName();
        String getBatchName();
        Instant getSubmittedAt();
        BigDecimal getEarnedMarks();
        BigDecimal getAvailableMarks();
        Long getPendingItemCount();
    }

    @Query("select a.attemptId as attemptId, a.examInstance.definitionName as testName,"
            + " a.examInstance.batch.name as batchName, a.submittedAt as submittedAt,"
            + " sum(case when q.markingStatus = com.learnium.model.exam.MarkingStatus.AUTO and q.correct = true then iq.marks"
            + " when q.markingStatus = com.learnium.model.exam.MarkingStatus.CONFIRMED then coalesce(q.confirmedScore, 0) else 0 end) as earnedMarks,"
            + " sum(iq.marks) as availableMarks,"
            + " sum(case when q.markingStatus not in (com.learnium.model.exam.MarkingStatus.AUTO, com.learnium.model.exam.MarkingStatus.CONFIRMED) then 1 else 0 end) as pendingItemCount"
            + " from QuestionAttempt q join q.attempt a join q.examInstanceQuestion iq"
            + " where a.centreId = :centreId and a.user.userId = :studentId"
            + " and a.status in (com.learnium.model.exam.ExamAttemptStatus.SUBMITTED, com.learnium.model.exam.ExamAttemptStatus.AUTO_SUBMITTED)"
            + " group by a.attemptId, a.examInstance.definitionName, a.examInstance.batch.name, a.submittedAt"
            + " order by a.submittedAt desc, a.attemptId desc")
    List<DashboardRecentFact> dashboardRecent(UUID centreId, UUID studentId, Pageable pageable);

    @Query(
            "select a from ExamAttempt a"
                    + " where a.centreId = :centreId"
                    + " and a.examInstance.examInstanceId = :examInstanceId")
    List<ExamAttempt> findByCentreIdAndExamInstanceId(
            @Param("centreId") UUID centreId, @Param("examInstanceId") UUID examInstanceId);

    @Query("select a from ExamAttempt a where a.centreId = :centreId and a.examInstance.examInstanceId = :instanceId"
            + " and a.user.userId = :userId order by a.attemptNo")
    List<ExamAttempt> ownedForInstance(UUID centreId, UUID instanceId, UUID userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExamAttempt a join fetch a.examInstance join fetch a.user"
            + " where a.centreId = :centreId and a.attemptId = :attemptId and a.user.userId = :userId")
    Optional<ExamAttempt> lockOwned(UUID centreId, UUID attemptId, UUID userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from ExamAttempt a join fetch a.examInstance join fetch a.user where a.attemptId = :attemptId")
    Optional<ExamAttempt> lockById(UUID attemptId);

    @Query("select a.attemptId from ExamAttempt a where a.status = :status"
            + " and (a.deadlineAt <= :now or a.examInstance.closedAt is not null)")
    List<UUID> dueAttemptIds(ExamAttemptStatus status, Instant now);
}
