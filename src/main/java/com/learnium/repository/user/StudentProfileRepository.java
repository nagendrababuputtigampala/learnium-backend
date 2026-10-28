package com.learnium.repository.user;

import com.learnium.model.user.StudentProfile;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {}
