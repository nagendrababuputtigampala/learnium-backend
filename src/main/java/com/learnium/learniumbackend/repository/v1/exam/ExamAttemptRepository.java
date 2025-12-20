package com.learnium.learniumbackend.repository.v1.exam;

import com.learnium.learniumbackend.model.v1.exam.ExamAttempt;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExamAttemptRepository extends JpaRepository<ExamAttempt, UUID> {

    @EntityGraph(attributePaths = {
            "user",
            "examTemplate",
            "grade",
            "subject",
            "topic",
            "examMode"
    })
    Optional<ExamAttempt> findByAttemptIdAndUser_UserId(UUID attemptId, UUID userId);
}