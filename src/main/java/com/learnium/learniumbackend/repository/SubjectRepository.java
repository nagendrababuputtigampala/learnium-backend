package com.learnium.learniumbackend.repository;

import com.learnium.learniumbackend.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface SubjectRepository extends JpaRepository<Subject, Integer> {
    @Query("SELECT s FROM GradeSubject gs " +
            "JOIN gs.subject s " +
            "JOIN gs.grade g " +
            "WHERE g.gradeId = :gradeId " +
            "ORDER BY s.subjectName")
    List<Subject> getSubjectsGradeId(@Param("gradeId") Integer gradeId);
}
