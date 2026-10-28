package com.learnium.repository.exam;

import com.learnium.model.exam.QuestionAttempt;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionAttemptRepository extends JpaRepository<QuestionAttempt, UUID> {

    /** Centre-scoped: a foreign centre's attempt id returns nothing, never its answers. */
    @Query(
            "select qa from QuestionAttempt qa"
                    + " where qa.centreId = :centreId and qa.attempt.attemptId = :attemptId")
    List<QuestionAttempt> findByCentreIdAndAttemptId(
            @Param("centreId") UUID centreId, @Param("attemptId") UUID attemptId);
}
