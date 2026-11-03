package com.learnium.repository.exam;

import com.learnium.model.exam.ProctorSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProctorSnapshotRepository extends JpaRepository<ProctorSnapshot, UUID> {

    List<ProctorSnapshot> findByCentreIdAndAttemptAttemptIdOrderByCapturedAtAsc(UUID centreId, UUID attemptId);

    Optional<ProctorSnapshot> findByCentreIdAndSnapshotId(UUID centreId, UUID snapshotId);

    Optional<ProctorSnapshot> findByCheckTokenHash(String checkTokenHash);

    List<ProctorSnapshot> findByCapturedAtBefore(Instant cutoff);
}
