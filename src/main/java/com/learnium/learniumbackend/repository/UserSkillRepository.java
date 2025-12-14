package com.learnium.learniumbackend.repository;

import com.learnium.learniumbackend.model.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface UserSkillRepository extends JpaRepository<UserSkill, UUID> {
    List<UserSkill> findByUserId(UUID userId);
}

