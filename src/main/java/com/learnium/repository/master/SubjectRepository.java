package com.learnium.repository.master;

import com.learnium.model.master.Subject;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    Optional<Subject> findBySubjectName(String subjectName);
}
