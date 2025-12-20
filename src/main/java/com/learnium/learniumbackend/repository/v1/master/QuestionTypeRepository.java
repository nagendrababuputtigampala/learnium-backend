package com.learnium.learniumbackend.repository.v1.master;

import java.util.Optional;

import com.learnium.learniumbackend.model.v1.master.QuestionType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionTypeRepository extends JpaRepository<QuestionType, Short> {
    Optional<QuestionType> findByNameIgnoreCase(String name);
}