package com.learnium.learniumbackend.repository.v1.content;
import com.learnium.learniumbackend.model.v1.content.ExamTemplateQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ExamTemplateQuestionRepository extends JpaRepository<ExamTemplateQuestion, UUID> {

    @Query("""
      select etq
      from ExamTemplateQuestion etq
      join fetch etq.question q
      where etq.examTemplate.examTemplateId = :templateId
        and etq.active = true
        and q.active = true
      order by etq.displayOrder asc
  """)
    List<ExamTemplateQuestion> findActiveQuestionsByTemplateId(@Param("templateId") UUID templateId);

    @Query("""
        select etq
        from ExamTemplateQuestion etq
        where etq.examTemplate.examTemplateId = :templateId
          and etq.active = true
        order by etq.displayOrder asc
    """)
    List<ExamTemplateQuestion> findActiveByTemplateIdOrdered(@Param("templateId") UUID templateId);

}