package com.learnium.repository.exam;

import com.learnium.model.exam.ExamInstanceQuestion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamInstanceQuestionRepository extends JpaRepository<ExamInstanceQuestion, UUID> {

    @Query(
            "select iq from ExamInstanceQuestion iq"
                    + " where iq.examInstance.examInstanceId = :examInstanceId"
                    + " order by iq.questionOrder asc")
    List<ExamInstanceQuestion> findByExamInstanceIdOrderByQuestionOrderAsc(
            @Param("examInstanceId") UUID examInstanceId);
}
