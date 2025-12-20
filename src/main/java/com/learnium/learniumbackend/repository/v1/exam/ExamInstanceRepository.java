package com.learnium.learniumbackend.repository.v1.exam;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.learnium.learniumbackend.model.v1.common.ExamInstanceStatus;
import com.learnium.learniumbackend.model.v1.exam.ExamInstance;
import com.learnium.learniumbackend.repository.v1.BaseRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

public interface ExamInstanceRepository extends BaseRepository<ExamInstance> {

    List<ExamInstance> findByUser_UserIdOrderByStartedAtDesc(UUID userId, Pageable pageable);

    Optional<ExamInstance> findByExamInstanceIdAndUser_UserId(UUID examInstanceId, UUID userId);

    @Query("""
        select ei
        from ExamInstance ei
        join fetch ei.examTemplate et
        join fetch et.examMode em
        where ei.examInstanceId = :examInstanceId
    """)
    Optional<ExamInstance> findByIdWithTemplate(UUID examInstanceId);

    @Query("""
        select ei
        from ExamInstance ei
        where ei.user.userId = :userId
          and ei.status = :status
          and ei.startedAt >= :from
        order by ei.startedAt desc
    """)
    List<ExamInstance> findUserInstancesSince(UUID userId, ExamInstanceStatus status, Instant from);

    // For "topic mastery trend" quickly (uses denorm attempts table later; but this helps too)
    @Query("""
        select count(ei.examInstanceId)
        from ExamInstance ei
        where ei.user.userId = :userId
          and ei.status = 'COMPLETED'
          and ei.startedAt >= :from
    """)
    long countCompletedSince(UUID userId, Instant from);
}