package com.learnium.repository.master;

import com.learnium.model.master.QuestionType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionTypeRepository extends JpaRepository<QuestionType, UUID> {

    Optional<QuestionType> findByCode(String code);
}
