package com.learnium.repository.exam;

import com.learnium.model.exam.ExamTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamTemplateRepository extends JpaRepository<ExamTemplate, UUID> {

    @Query("select t from ExamTemplate t join fetch t.gradeSubject gs join fetch gs.grade join fetch gs.subject where t.centre.centreId = :centreId and t.active = true and (:admin = true or t.createdBy = :userId) order by t.createdAt desc, t.examTemplateId")
    List<ExamTemplate> visible(UUID centreId, UUID userId, boolean admin);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ExamTemplate t where t.centre.centreId = :centreId and t.examTemplateId = :id and t.active = true and (:admin = true or t.createdBy = :userId)")
    java.util.Optional<ExamTemplate> owned(UUID centreId, UUID userId, boolean admin, UUID id);

    @Query("select t from ExamTemplate t where t.centre.centreId = :centreId")
    List<ExamTemplate> findByCentreId(@Param("centreId") UUID centreId);
}
