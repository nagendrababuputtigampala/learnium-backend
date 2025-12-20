package com.learnium.learniumbackend.repository.v1.master;

import java.util.List;
import java.util.UUID;

import com.learnium.learniumbackend.model.v1.common.ExamModeType;
import com.learnium.learniumbackend.model.v1.master.ExamMode;
import com.learnium.learniumbackend.repository.v1.BaseRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamModeRepository extends BaseRepository<ExamMode> {

    @Query("""
        select m
        from ExamMode m
        where m.active = true
        order by m.sortOrder asc, m.title asc
    """)
    List<ExamMode> findActiveOrdered();

    List<ExamMode> findByTestTypeAndActiveTrueOrderBySortOrderAscTitleAsc(ExamModeType testType);

    @Query("""
  select distinct em
  from ExamTemplate et
  join et.examMode em
  join et.topic t
  join t.gradeSubject gs
  join gs.grade g
  join gs.subject s
  where et.active = true
    and em.active = true
    and t.active = true
    and gs.active = true
    and g.gradeId = :gradeId
    and s.subjectId = :subjectId
    and t.topicId = :topicId
  order by em.sortOrder asc, em.title asc
""")
    List<ExamMode> findAvailableModes(UUID gradeId, UUID subjectId, UUID topicId);
}
