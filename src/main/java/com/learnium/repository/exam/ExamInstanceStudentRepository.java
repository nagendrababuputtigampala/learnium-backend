package com.learnium.repository.exam;

import com.learnium.model.exam.ExamInstanceStudent;
import java.util.*;
import java.math.BigDecimal;
import java.time.Instant;
import com.learnium.model.exam.ExamAttemptStatus;
import org.springframework.data.jpa.repository.*;

public interface ExamInstanceStudentRepository extends JpaRepository<ExamInstanceStudent, UUID> {
    @Query("select s from ExamInstanceStudent s join fetch s.user where s.centreId = :centreId and s.examInstance.examInstanceId = :instanceId order by s.addedAt, s.id")
    List<ExamInstanceStudent> assigned(UUID centreId, UUID instanceId);
    boolean existsByExamInstanceExamInstanceIdAndUserUserId(UUID instanceId, UUID userId);
    @Query("select s from ExamInstanceStudent s join fetch s.examInstance i join fetch s.user"
            + " where s.centreId = :centreId and i.examInstanceId = :instanceId and s.user.userId = :userId")
    Optional<ExamInstanceStudent> findAssigned(UUID centreId, UUID instanceId, UUID userId);

    interface StudentTestRow {
        UUID getInstanceId(); String getDefinitionName(); String getDefinitionKind(); String getBatchName();
        Instant getOpensAt(); Instant getClosesAt(); Instant getClosedAt(); int getMaxAttempts();
        UUID getAttemptId(); Integer getAttemptNo(); ExamAttemptStatus getAttemptStatus(); Instant getSubmittedAt();
        BigDecimal getTotalScore(); boolean getMarkingPending();
    }

    @Query("select i.examInstanceId as instanceId, i.definitionName as definitionName, i.definitionKind as definitionKind,"
            + " i.batch.name as batchName, i.opensAt as opensAt, i.closesAt as closesAt, i.closedAt as closedAt,"
            + " i.maxAttempts as maxAttempts, a.attemptId as attemptId, a.attemptNo as attemptNo, a.status as attemptStatus,"
            + " a.submittedAt as submittedAt, a.totalScore as totalScore,"
            + " case when count(qa.questionAttemptId) > 0 then true else false end as markingPending"
            + " from ExamInstanceStudent s join s.examInstance i"
            + " left join ExamAttempt a on a.examInstance = i and a.user.userId = :userId and a.centreId = :centreId"
            + " left join QuestionAttempt qa on qa.attempt = a and qa.markingStatus in"
            + " (com.learnium.model.exam.MarkingStatus.PENDING, com.learnium.model.exam.MarkingStatus.MARKING_PENDING)"
            + " where s.centreId = :centreId and s.user.userId = :userId"
            + " group by i.examInstanceId, i.definitionName, i.definitionKind, i.batch.name, i.opensAt, i.closesAt,"
            + " i.closedAt, i.maxAttempts, a.attemptId, a.attemptNo, a.status, a.submittedAt, a.totalScore"
            + " order by i.opensAt, i.examInstanceId, a.attemptNo")
    List<StudentTestRow> studentTests(UUID centreId, UUID userId);
}
