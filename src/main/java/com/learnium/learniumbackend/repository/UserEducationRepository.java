package com.learnium.learniumbackend.repository;

import com.learnium.learniumbackend.model.UserEducation;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface UserEducationRepository extends JpaRepository<UserEducation, UUID> {
    List<UserEducation> findByUserId(UUID userId);
}

