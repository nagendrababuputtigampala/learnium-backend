package com.learnium.learniumbackend.repository.v1.content;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.learnium.learniumbackend.model.v1.content.GradeSubject;
import com.learnium.learniumbackend.repository.v1.BaseRepository;
import org.springframework.data.jpa.repository.Query;

public interface GradeSubjectRepository extends BaseRepository<GradeSubject> {

    Optional<GradeSubject> findByGrade_GradeIdAndSubject_SubjectId(UUID gradeId, UUID subjectId);

    @Query("""
        select gs
        from GradeSubject gs
        join fetch gs.subject s
        where gs.grade.gradeId = :gradeId
          and gs.active = true
          and s.active = true
        order by gs.sortOrder asc, s.subjectName asc
    """)
    List<GradeSubject> findActiveSubjectsByGrade(UUID gradeId);

    @Query("""
        select gs.gradeSubjectId
        from GradeSubject gs
        where gs.grade.gradeId = :gradeId
          and gs.subject.subjectId = :subjectId
    """)
    Optional<UUID> findId(UUID gradeId, UUID subjectId);
}