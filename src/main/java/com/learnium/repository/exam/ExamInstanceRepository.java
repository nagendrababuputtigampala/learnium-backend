package com.learnium.repository.exam;

import com.learnium.model.exam.ExamInstance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamInstanceRepository extends JpaRepository<ExamInstance, UUID> {

    @Query("select i from ExamInstance i where i.batch.batchId = :batchId")
    List<ExamInstance> findByBatchId(@Param("batchId") UUID batchId);

    /** Student entry point lookup: the join code identifies the published test. */
    Optional<ExamInstance> findByJoinCode(String joinCode);
}
