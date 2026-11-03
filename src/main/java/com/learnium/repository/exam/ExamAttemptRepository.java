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

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, UUID> {

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
