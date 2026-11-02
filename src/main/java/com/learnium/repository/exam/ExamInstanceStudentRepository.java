package com.learnium.repository.exam;

import com.learnium.model.exam.ExamInstanceStudent;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface ExamInstanceStudentRepository extends JpaRepository<ExamInstanceStudent, UUID> {
    @Query("select s from ExamInstanceStudent s join fetch s.user where s.centreId = :centreId and s.examInstance.examInstanceId = :instanceId order by s.addedAt, s.id")
    List<ExamInstanceStudent> assigned(UUID centreId, UUID instanceId);
    boolean existsByExamInstanceExamInstanceIdAndUserUserId(UUID instanceId, UUID userId);
}
