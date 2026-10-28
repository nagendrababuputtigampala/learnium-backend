package com.learnium.repository.master;

import com.learnium.model.master.GradeSubject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GradeSubjectRepository extends JpaRepository<GradeSubject, UUID> {

    @Query(
            "select gs from GradeSubject gs"
                    + " where gs.grade.gradeId = :gradeId and gs.subject.subjectId = :subjectId")
    Optional<GradeSubject> findByGradeIdAndSubjectId(
            @Param("gradeId") UUID gradeId, @Param("subjectId") UUID subjectId);

    /** Full pairing catalogue for pickers, grade + subject fetched eagerly, in display order. */
    @Query(
            "select gs from GradeSubject gs"
                    + " join fetch gs.grade join fetch gs.subject"
                    + " order by gs.grade.sortOrder asc, gs.subject.subjectName asc")
    List<GradeSubject> findAllForListing();
}
