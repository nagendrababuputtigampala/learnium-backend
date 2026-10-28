package com.learnium.repository.exam;

import com.learnium.model.exam.ExamAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, UUID> {

    @Query(
            "select a from ExamAttempt a"
                    + " where a.centreId = :centreId"
                    + " and a.examInstance.examInstanceId = :examInstanceId")
    List<ExamAttempt> findByCentreIdAndExamInstanceId(
            @Param("centreId") UUID centreId, @Param("examInstanceId") UUID examInstanceId);
}
