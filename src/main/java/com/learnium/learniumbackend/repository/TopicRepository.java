package com.learnium.learniumbackend.repository;

import com.learnium.learniumbackend.model.Subject;
import com.learnium.learniumbackend.model.Topic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TopicRepository extends JpaRepository<Topic, Integer> {
    @Query("""
    SELECT DISTINCT t
    FROM GradeSubject gs
    JOIN gs.grade g
    JOIN gs.subject s
    JOIN gs.topics gst
    JOIN gst.topic t
    WHERE g.gradeId = :gradeId
      AND s.subjectId = :subjectId
    ORDER BY t.topicName
""")
    List<Topic> findTopicsByGradeAndSubject(
            @Param("gradeId") Integer gradeId,
            @Param("subjectId") Integer subjectId
    );
}
