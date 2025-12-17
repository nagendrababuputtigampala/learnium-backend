package com.learnium.learniumbackend.repository;

import com.learnium.learniumbackend.model.Exam;
import com.learnium.learniumbackend.model.ExamMode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamListRepository extends JpaRepository<Exam, Integer> {

    @Query("""
    SELECT e
    FROM Exam e
    JOIN e.topicExamMode em
    JOIN e.topicExamMode tem
    JOIN tem.gradeSubjectTopic gst
    JOIN gst.topic t
    JOIN gst.gradeSubject gs
    JOIN gs.grade g
    JOIN gs.subject s
    WHERE g.gradeId = :gradeId
      AND s.subjectId = :subjectId
      AND t.topicId = :topicId
      AND em.topicExamModeId = :examModeId
""")
    List<Exam> findExamsByFullFilter(
            Integer gradeId,
            Integer subjectId,
            Integer topicId,
            Integer examModeId
    );
}
