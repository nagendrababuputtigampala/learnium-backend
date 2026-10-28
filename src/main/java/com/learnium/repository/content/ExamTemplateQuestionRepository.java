package com.learnium.repository.content;

import com.learnium.model.content.ExamTemplateQuestion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamTemplateQuestionRepository extends JpaRepository<ExamTemplateQuestion, UUID> {

    @Query(
            "select tq from ExamTemplateQuestion tq"
                    + " where tq.centreId = :centreId"
                    + " and tq.examTemplate.examTemplateId = :examTemplateId"
                    + " order by tq.displayOrder asc")
    List<ExamTemplateQuestion> findByCentreIdAndExamTemplateIdOrderByDisplayOrderAsc(
            @Param("centreId") UUID centreId, @Param("examTemplateId") UUID examTemplateId);
}
