package com.learnium.repository.master;

import com.learnium.model.common.ExamModeType;
import com.learnium.model.master.ExamMode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExamModeRepository extends JpaRepository<ExamMode, UUID> {

    Optional<ExamMode> findByTestType(ExamModeType testType);
}
