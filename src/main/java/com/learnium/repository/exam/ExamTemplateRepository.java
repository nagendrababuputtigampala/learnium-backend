package com.learnium.repository.exam;

import com.learnium.model.exam.ExamTemplate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamTemplateRepository extends JpaRepository<ExamTemplate, UUID> {

    @Query("select t from ExamTemplate t where t.centre.centreId = :centreId")
    List<ExamTemplate> findByCentreId(@Param("centreId") UUID centreId);
}
