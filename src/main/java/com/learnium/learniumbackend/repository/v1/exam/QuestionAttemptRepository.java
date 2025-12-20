package com.learnium.learniumbackend.repository.v1.exam;

import com.learnium.learniumbackend.model.v1.exam.QuestionAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface QuestionAttemptRepository extends JpaRepository<QuestionAttempt, UUID> {
    @Query("""
        select qa
        from QuestionAttempt qa
          join fetch qa.question q
          join fetch q.questionType qt
        where qa.attempt.attemptId = :attemptId
        order by qa.questionAttemptId
    """)
    List<QuestionAttempt> findForReview(UUID attemptId);
}