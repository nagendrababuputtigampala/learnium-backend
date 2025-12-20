package com.learnium.learniumbackend.repository.v1.exam;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.learnium.learniumbackend.model.v1.exam.ExamTemplate;
import com.learnium.learniumbackend.repository.v1.BaseRepository;
import org.springframework.data.jpa.repository.Query;

public interface ExamTemplateRepository extends BaseRepository<ExamTemplate> {

    @Query("""
        select et
        from ExamTemplate et
        where et.grade.gradeId = :gradeId
          and et.subject.subjectId = :subjectId
          and ((:topicId is null and et.topic is null) or (et.topic.topicId = :topicId))
          and et.examMode.examModeId = :examModeId
          and et.active = true
        order by et.version desc
    """)
    List<ExamTemplate> findActiveTemplates(UUID gradeId, UUID subjectId, UUID topicId, UUID examModeId);

    @Query("""
        select et
        from ExamTemplate et
        join fetch et.examMode em
        join fetch et.grade g
        join fetch et.subject s
        left join fetch et.topic t
        where et.examTemplateId = :templateId
    """)
    Optional<ExamTemplate> findByIdWithRefs(UUID templateId);

    Optional<ExamTemplate> findFirstByGrade_GradeIdAndSubject_SubjectIdAndTopic_TopicIdAndExamMode_ExamModeIdAndActiveTrueOrderByVersionDesc(
            UUID gradeId, UUID subjectId, UUID topicId, UUID examModeId
    );
}