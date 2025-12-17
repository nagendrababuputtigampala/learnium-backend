package com.learnium.learniumbackend.repository;

import com.learnium.learniumbackend.model.Exam;
import com.learnium.learniumbackend.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface Questionsepository extends JpaRepository<Question, Integer> {

    @Query("""
        SELECT DISTINCT q
        FROM Question q
        JOIN q.exam e
        JOIN e.topicExamMode tem
        JOIN tem.gradeSubjectTopic gst
        JOIN gst.topic t
        JOIN gst.gradeSubject gs
        JOIN gs.grade g
        JOIN gs.subject s
        JOIN FETCH q.options o
        WHERE g.gradeId = :gradeId
          AND s.subjectId = :subjectId
          AND t.topicId = :topicId
          AND tem.examMode.examModeId = :examModeId
          AND e.examId = :examId
    """)
    List<Question> findQuestionsWithOptions(
            Integer gradeId,
            Integer subjectId,
            Integer topicId,
            Integer examModeId,
            Integer examId
    );
}
