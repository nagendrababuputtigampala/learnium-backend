package com.learnium.learniumbackend.repository;

import com.learnium.learniumbackend.model.UserCertificate;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface UserCertificateRepository extends JpaRepository<UserCertificate, UUID> {
    List<UserCertificate> findByUserId(UUID userId);
}

