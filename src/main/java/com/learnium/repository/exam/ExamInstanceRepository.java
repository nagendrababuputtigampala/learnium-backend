package com.learnium.repository.exam;

import com.learnium.model.exam.ExamInstance;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamInstanceRepository extends JpaRepository<ExamInstance, UUID> {

    interface PublicationCount { UUID getTestId(); long getCount(); }
    @Query("select i.examTemplate.examTemplateId as testId, count(i) as count from ExamInstance i where i.centreId = :centreId group by i.examTemplate.examTemplateId")
    List<PublicationCount> publicationCounts(UUID centreId);
    boolean existsByJoinCode(String code);
    long countByExamTemplateExamTemplateId(UUID id);

    @Query("select i from ExamInstance i join fetch i.batch join fetch i.examTemplate where i.centreId = :centreId and (:batchId is null or i.batch.batchId = :batchId) and (:admin = true or exists (select m from BatchMembership m where m.centreId = :centreId and m.batch = i.batch and m.user.userId = :userId and m.memberRole = com.learnium.model.user.MemberRole.TEACHER and m.status = com.learnium.model.user.MembershipStatus.ACTIVE)) order by i.createdAt desc, i.examInstanceId")
    List<ExamInstance> manageable(UUID centreId, UUID userId, boolean admin, UUID batchId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from ExamInstance i where i.centreId = :centreId and i.examInstanceId = :id")
    Optional<ExamInstance> lockInCentre(UUID centreId, UUID id);

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
