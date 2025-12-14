package com.learnium.learniumbackend.repository;

import com.learnium.learniumbackend.model.UserLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface UserLinkRepository extends JpaRepository<UserLink, UUID> {
    List<UserLink> findByUserId(UUID userId);
}

