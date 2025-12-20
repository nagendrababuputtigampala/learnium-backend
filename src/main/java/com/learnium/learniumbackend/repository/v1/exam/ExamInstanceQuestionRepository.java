package com.learnium.learniumbackend.repository.v1.exam;

import java.util.List;
import java.util.UUID;

import com.learnium.learniumbackend.model.v1.exam.ExamInstanceQuestion;
import com.learnium.learniumbackend.repository.v1.BaseRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExamInstanceQuestionRepository extends BaseRepository<ExamInstanceQuestion> {

    @Query("""
        select eiq
        from ExamInstanceQuestion eiq
        join fetch eiq.question q
        where eiq.examInstance.examInstanceId = :examInstanceId
        order by eiq.questionOrder asc
    """)
    List<ExamInstanceQuestion> findByExamInstanceIdOrderedWithQuestion(UUID examInstanceId);

    @Query("""
        select eiq.question.questionId
        from ExamInstanceQuestion eiq
        where eiq.examInstance.examInstanceId = :examInstanceId
        order by eiq.questionOrder asc
    """)
    List<UUID> findQuestionIdsByExamInstance(UUID examInstanceId);
}
