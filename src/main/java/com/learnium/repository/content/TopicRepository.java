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

    /** Picker list for one grade-subject pairing, in display (name) order. */
    @Query(
            "select t from Topic t"
                    + " where t.gradeSubject.gradeSubjectId = :gradeSubjectId"
                    + " order by t.topicName asc")
    List<Topic> findByGradeSubjectIdOrderByTopicNameAsc(
            @Param("gradeSubjectId") UUID gradeSubjectId);

    @Query(
            "select t from Topic t"
                    + " where t.gradeSubject.gradeSubjectId = :gradeSubjectId"
                    + " and t.topicName = :topicName")
    Optional<Topic> findByGradeSubjectIdAndTopicName(
            @Param("gradeSubjectId") UUID gradeSubjectId, @Param("topicName") String topicName);

    /**
     * Case-insensitive name match for create-or-return: "fractions" must
     * find an existing "Fractions" instead of creating a second topic that
     * differs only by casing. A list (earliest first) rather than an
     * Optional, because the database constraint is case-sensitive and
     * legacy data could in principle hold two casings of one name.
     */
    @Query(
            "select t from Topic t"
                    + " where t.gradeSubject.gradeSubjectId = :gradeSubjectId"
                    + " and lower(t.topicName) = lower(:topicName)"
                    + " order by t.createdAt asc")
    List<Topic> findByGradeSubjectIdAndTopicNameIgnoreCase(
            @Param("gradeSubjectId") UUID gradeSubjectId, @Param("topicName") String topicName);
}
