package com.learnium.learniumbackend.repository;

import com.learnium.learniumbackend.model.ExamMode;
import com.learnium.learniumbackend.model.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamModeRepository extends JpaRepository<ExamMode, Integer> {

    @Query("""
    SELECT DISTINCT em
    FROM GradeSubject gs
    JOIN gs.grade g
    JOIN gs.subject s
    JOIN gs.topics gst
    JOIN gst.topic t
    JOIN gst.topicExamModes tem
    JOIN tem.examMode em
    WHERE g.gradeId = :gradeId
      AND s.subjectId = :subjectId
      AND t.topicId = :topicId
    ORDER BY em.modeName
""")
    List<ExamMode> findExamModesByGradeSubjectTopic(
            @Param("gradeId") Integer gradeId,
            @Param("subjectId") Integer subjectId,
            @Param("topicId") Integer topicId
    );
}
