package com.learnium.repository.master;

import com.learnium.model.master.Grade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeRepository extends JpaRepository<Grade, UUID> {

    Optional<Grade> findByCode(String code);
}
