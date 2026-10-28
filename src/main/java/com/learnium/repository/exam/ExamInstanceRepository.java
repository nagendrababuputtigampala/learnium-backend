package com.learnium.repository.exam;

import com.learnium.model.exam.ExamInstance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamInstanceRepository extends JpaRepository<ExamInstance, UUID> {

    @Query(
            "select i from ExamInstance i"
                    + " where i.centreId = :centreId and i.batch.batchId = :batchId")
    List<ExamInstance> findByCentreIdAndBatchId(
            @Param("centreId") UUID centreId, @Param("batchId") UUID batchId);

    /**
     * Student entry point lookup: the join code identifies the published test, so
     * this finder is intentionally global. Tenancy and roster access are
     * established from the returned instance before anything else is read.
     */
    Optional<ExamInstance> findByJoinCode(String joinCode);
}
