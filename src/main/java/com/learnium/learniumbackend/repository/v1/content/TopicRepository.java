package com.learnium.learniumbackend.repository.v1.content;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.learnium.learniumbackend.model.v1.content.Topic;
import com.learnium.learniumbackend.repository.v1.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends BaseRepository<Topic> {

    @Query("""
        select t
        from Topic t
        where t.gradeSubject.gradeSubjectId = :gradeSubjectId
          and t.active = true
        order by t.sortOrder asc, t.topicName asc
    """)
    List<Topic> findActiveTopics(UUID gradeSubjectId);

    Optional<Topic> findByGradeSubject_GradeSubjectIdAndTopicNameIgnoreCase(UUID gradeSubjectId, String topicName);

    @Query("""
        select t
        from Topic t
        join fetch t.gradeSubject gs
        join fetch gs.grade g
        join fetch gs.subject s
        where t.topicId = :topicId
    """)
    Optional<Topic> findByIdWithHierarchy(UUID topicId);

    @Query("""
      select t
      from Topic t
      join t.gradeSubject gs
      join gs.grade g
      join gs.subject s
      where t.active = true
        and gs.active = true
        and g.gradeId = :gradeId
        and s.subjectId = :subjectId
      order by coalesce(t.sortOrder, 999999) asc, t.topicName asc
  """)
    List<Topic> findActiveTopicsByGradeAndSubject(
            @Param("gradeId") UUID gradeId,
            @Param("subjectId") UUID subjectId
    );
}