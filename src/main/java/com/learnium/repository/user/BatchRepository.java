package com.learnium.repository.user;

import com.learnium.model.user.Batch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BatchRepository extends JpaRepository<Batch, UUID> {

    @Query("select b from Batch b where b.centre.centreId = :centreId")
    List<Batch> findByCentreId(@Param("centreId") UUID centreId);

    /** Centre-scoped single-batch read for tenant aggregates (never a bare findById). */
    @Query(
            "select b from Batch b"
                    + " where b.centre.centreId = :centreId and b.batchId = :batchId")
    Optional<Batch> findByCentreIdAndBatchId(
            @Param("centreId") UUID centreId, @Param("batchId") UUID batchId);
}
