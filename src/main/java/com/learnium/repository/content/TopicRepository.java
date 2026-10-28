package com.learnium.repository.content;

import com.learnium.model.content.Topic;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends JpaRepository<Topic, UUID> {

    @Query("select t from Topic t where t.gradeSubject.gradeSubjectId = :gradeSubjectId")
    List<Topic> findByGradeSubjectId(@Param("gradeSubjectId") UUID gradeSubjectId);

    @Query(
            "select t from Topic t"
                    + " where t.gradeSubject.gradeSubjectId = :gradeSubjectId"
                    + " and t.topicName = :topicName")
    Optional<Topic> findByGradeSubjectIdAndTopicName(
            @Param("gradeSubjectId") UUID gradeSubjectId, @Param("topicName") String topicName);
}
