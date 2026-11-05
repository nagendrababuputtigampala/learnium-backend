package com.learnium.repository.exam;

import com.learnium.model.exam.QuestionAttempt;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionAttemptRepository extends JpaRepository<QuestionAttempt, UUID> {

    interface DashboardTopicFact {
        UUID getTopicId();
        String getTopicName();
        Long getConfirmedItemCount();
        BigDecimal getEarnedMarks();
        BigDecimal getAvailableMarks();
        Long getPendingItemCount();
    }

    interface DashboardComparisonFact {
        UUID getBatchId();
        String getBatchName();
        UUID getTopicId();
        String getTopicName();
        UUID getStudentId();
        Long getConfirmedItemCount();
        BigDecimal getEarnedMarks();
        BigDecimal getAvailableMarks();
    }

    @Query("select q.question.topic.topicId as topicId, q.question.topic.topicName as topicName,"
            + " sum(case when q.markingStatus in (com.learnium.model.exam.MarkingStatus.AUTO, com.learnium.model.exam.MarkingStatus.CONFIRMED) then 1 else 0 end) as confirmedItemCount,"
            + " sum(case when q.markingStatus = com.learnium.model.exam.MarkingStatus.AUTO and q.correct = true then iq.marks"
            + " when q.markingStatus = com.learnium.model.exam.MarkingStatus.CONFIRMED then coalesce(q.confirmedScore, 0) else 0 end) as earnedMarks,"
            + " sum(case when q.markingStatus in (com.learnium.model.exam.MarkingStatus.AUTO, com.learnium.model.exam.MarkingStatus.CONFIRMED) then iq.marks else 0 end) as availableMarks,"
            + " sum(case when q.markingStatus not in (com.learnium.model.exam.MarkingStatus.AUTO, com.learnium.model.exam.MarkingStatus.CONFIRMED) then 1 else 0 end) as pendingItemCount"
            + " from QuestionAttempt q join q.attempt a join q.examInstanceQuestion iq"
            + " where q.centreId = :centreId and a.user.userId = :studentId"
            + " and a.status in (com.learnium.model.exam.ExamAttemptStatus.SUBMITTED, com.learnium.model.exam.ExamAttemptStatus.AUTO_SUBMITTED)"
            + " group by q.question.topic.topicId, q.question.topic.topicName order by q.question.topic.topicName")
    List<DashboardTopicFact> dashboardTopicFacts(UUID centreId, UUID studentId);

    @Query("select a.examInstance.batch.batchId as batchId, a.examInstance.batch.name as batchName,"
            + " q.question.topic.topicId as topicId, q.question.topic.topicName as topicName,"
            + " a.user.userId as studentId, count(q) as confirmedItemCount,"
            + " sum(case when q.markingStatus = com.learnium.model.exam.MarkingStatus.AUTO and q.correct = true then iq.marks"
            + " when q.markingStatus = com.learnium.model.exam.MarkingStatus.CONFIRMED then coalesce(q.confirmedScore, 0) else 0 end) as earnedMarks,"
            + " sum(iq.marks) as availableMarks"
            + " from QuestionAttempt q join q.attempt a join q.examInstanceQuestion iq"
            + " where q.centreId = :centreId"
            + " and a.status in (com.learnium.model.exam.ExamAttemptStatus.SUBMITTED, com.learnium.model.exam.ExamAttemptStatus.AUTO_SUBMITTED)"
            + " and q.markingStatus in (com.learnium.model.exam.MarkingStatus.AUTO, com.learnium.model.exam.MarkingStatus.CONFIRMED)"
            + " and exists (select owned from ExamAttempt owned where owned.centreId = :centreId"
            + " and owned.user.userId = :studentId and owned.examInstance.batch = a.examInstance.batch"
            + " and owned.status in (com.learnium.model.exam.ExamAttemptStatus.SUBMITTED, com.learnium.model.exam.ExamAttemptStatus.AUTO_SUBMITTED))"
            + " group by a.examInstance.batch.batchId, a.examInstance.batch.name, q.question.topic.topicId,"
            + " q.question.topic.topicName, a.user.userId")
    List<DashboardComparisonFact> dashboardComparisonFacts(UUID centreId, UUID studentId);

    /** M6 report facts for one student, optionally scoped to one batch (null = all batches). */
    @Query("select q.question.topic.topicId as topicId, q.question.topic.topicName as topicName,"
            + " sum(case when q.markingStatus in (com.learnium.model.exam.MarkingStatus.AUTO, com.learnium.model.exam.MarkingStatus.CONFIRMED) then 1 else 0 end) as confirmedItemCount,"
            + " sum(case when q.markingStatus = com.learnium.model.exam.MarkingStatus.AUTO and q.correct = true then iq.marks"
            + " when q.markingStatus = com.learnium.model.exam.MarkingStatus.CONFIRMED then coalesce(q.confirmedScore, 0) else 0 end) as earnedMarks,"
            + " sum(case when q.markingStatus in (com.learnium.model.exam.MarkingStatus.AUTO, com.learnium.model.exam.MarkingStatus.CONFIRMED) then iq.marks else 0 end) as availableMarks,"
            + " sum(case when q.markingStatus not in (com.learnium.model.exam.MarkingStatus.AUTO, com.learnium.model.exam.MarkingStatus.CONFIRMED) then 1 else 0 end) as pendingItemCount"
            + " from QuestionAttempt q join q.attempt a join q.examInstanceQuestion iq"
            + " where q.centreId = :centreId and a.user.userId = :studentId"
            + " and (:batchId is null or a.examInstance.batch.batchId = :batchId)"
            + " and a.status in (com.learnium.model.exam.ExamAttemptStatus.SUBMITTED, com.learnium.model.exam.ExamAttemptStatus.AUTO_SUBMITTED)"
            + " group by q.question.topic.topicId, q.question.topic.topicName order by q.question.topic.topicName")
    List<DashboardTopicFact> reportTopicFacts(UUID centreId, UUID studentId, UUID batchId);

    interface ReportAttemptFact {
        UUID getAttemptId();
        String getTestName();
        String getBatchName();
        java.time.Instant getSubmittedAt();
        BigDecimal getEarnedMarks();
        BigDecimal getAvailableMarks();
        Long getPendingItemCount();
    }

    /** M6 per-attempt facts for one student (trend/history), oldest first. */
    @Query("select a.attemptId as attemptId, a.examInstance.definitionName as testName,"
            + " a.examInstance.batch.name as batchName, a.submittedAt as submittedAt,"
            + " sum(case when q.markingStatus = com.learnium.model.exam.MarkingStatus.AUTO and q.correct = true then iq.marks"
            + " when q.markingStatus = com.learnium.model.exam.MarkingStatus.CONFIRMED then coalesce(q.confirmedScore, 0) else 0 end) as earnedMarks,"
            + " sum(case when q.markingStatus in (com.learnium.model.exam.MarkingStatus.AUTO, com.learnium.model.exam.MarkingStatus.CONFIRMED) then iq.marks else 0 end) as availableMarks,"
            + " sum(case when q.markingStatus not in (com.learnium.model.exam.MarkingStatus.AUTO, com.learnium.model.exam.MarkingStatus.CONFIRMED) then 1 else 0 end) as pendingItemCount"
            + " from QuestionAttempt q join q.attempt a join q.examInstanceQuestion iq"
            + " where q.centreId = :centreId and a.user.userId = :studentId"
            + " and (:batchId is null or a.examInstance.batch.batchId = :batchId)"
            + " and a.status in (com.learnium.model.exam.ExamAttemptStatus.SUBMITTED, com.learnium.model.exam.ExamAttemptStatus.AUTO_SUBMITTED)"
            + " group by a.attemptId, a.examInstance.definitionName, a.examInstance.batch.name, a.submittedAt"
            + " order by a.submittedAt asc, a.attemptId asc")
    List<ReportAttemptFact> reportAttemptFacts(UUID centreId, UUID studentId, UUID batchId);

    interface BatchTopicFactRow {
        UUID getTopicId();
        String getTopicName();
        Long getConfirmedItemCount();
        Long getPendingItemCount();
        BigDecimal getEarnedMarks();
        BigDecimal getAvailableMarks();
    }

    /** M6 per-topic aggregates over every student of one batch. */
    @Query("select q.question.topic.topicId as topicId, q.question.topic.topicName as topicName,"
            + " sum(case when q.markingStatus in (com.learnium.model.exam.MarkingStatus.AUTO, com.learnium.model.exam.MarkingStatus.CONFIRMED) then 1 else 0 end) as confirmedItemCount,"
            + " sum(case when q.markingStatus not in (com.learnium.model.exam.MarkingStatus.AUTO, com.learnium.model.exam.MarkingStatus.CONFIRMED) then 1 else 0 end) as pendingItemCount,"
            + " sum(case when q.markingStatus = com.learnium.model.exam.MarkingStatus.AUTO and q.correct = true then iq.marks"
            + " when q.markingStatus = com.learnium.model.exam.MarkingStatus.CONFIRMED then coalesce(q.confirmedScore, 0) else 0 end) as earnedMarks,"
            + " sum(case when q.markingStatus in (com.learnium.model.exam.MarkingStatus.AUTO, com.learnium.model.exam.MarkingStatus.CONFIRMED) then iq.marks else 0 end) as availableMarks"
            + " from QuestionAttempt q join q.attempt a join q.examInstanceQuestion iq"
            + " where q.centreId = :centreId and a.examInstance.batch.batchId = :batchId"
            + " and a.status in (com.learnium.model.exam.ExamAttemptStatus.SUBMITTED, com.learnium.model.exam.ExamAttemptStatus.AUTO_SUBMITTED)"
            + " group by q.question.topic.topicId, q.question.topic.topicName")
    List<BatchTopicFactRow> batchTopicFacts(UUID centreId, UUID batchId);

    interface BatchTopicStudentCount {
        UUID getTopicId();
        Long getStudentCount();
    }

    /** Distinct students with at least one confirmed item per topic in one batch. */
    @Query("select q.question.topic.topicId as topicId, count(distinct a.user.userId) as studentCount"
            + " from QuestionAttempt q join q.attempt a"
            + " where q.centreId = :centreId and a.examInstance.batch.batchId = :batchId"
            + " and a.status in (com.learnium.model.exam.ExamAttemptStatus.SUBMITTED, com.learnium.model.exam.ExamAttemptStatus.AUTO_SUBMITTED)"
            + " and q.markingStatus in (com.learnium.model.exam.MarkingStatus.AUTO, com.learnium.model.exam.MarkingStatus.CONFIRMED)"
            + " group by q.question.topic.topicId")
    List<BatchTopicStudentCount> batchTopicStudentCounts(UUID centreId, UUID batchId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from QuestionAttempt q where q.questionAttemptId = :id")
    java.util.Optional<QuestionAttempt> lockForMarking(UUID id);

    @Query("select distinct q from QuestionAttempt q join fetch q.attempt a join fetch a.user join fetch a.examInstance i join fetch i.batch join fetch q.examInstanceQuestion iq join fetch iq.question qu join fetch qu.questionType where q.centreId = :centreId and q.markingStatus = com.learnium.model.exam.MarkingStatus.PENDING and a.status in (com.learnium.model.exam.ExamAttemptStatus.SUBMITTED, com.learnium.model.exam.ExamAttemptStatus.AUTO_SUBMITTED) and (:admin = true or exists (select m from BatchMembership m where m.centreId = :centreId and m.batch = i.batch and m.user.userId = :userId and m.memberRole = com.learnium.model.user.MemberRole.TEACHER and m.status = com.learnium.model.user.MembershipStatus.ACTIVE)) order by i.createdAt desc, a.submittedAt, q.questionAttemptId")
    java.util.List<QuestionAttempt> markingQueue(UUID centreId, UUID userId, boolean admin);

    @Query("select distinct q from QuestionAttempt q join fetch q.attempt a join fetch a.user join fetch a.examInstance i join fetch i.batch join fetch q.examInstanceQuestion iq join fetch iq.question qu join fetch qu.questionType where q.centreId = :centreId and q.markingStatus = com.learnium.model.exam.MarkingStatus.PENDING and a.status in (com.learnium.model.exam.ExamAttemptStatus.SUBMITTED, com.learnium.model.exam.ExamAttemptStatus.AUTO_SUBMITTED) and (:admin = true or exists (select m from BatchMembership m where m.centreId = :centreId and m.batch = i.batch and m.user.userId = :userId and m.memberRole = com.learnium.model.user.MemberRole.TEACHER and m.status = com.learnium.model.user.MembershipStatus.ACTIVE)) order by i.createdAt desc, a.submittedAt, q.questionAttemptId")
    java.util.List<QuestionAttempt> markingQueue(UUID centreId, UUID userId, boolean admin, org.springframework.data.domain.Pageable pageable);

    @Query("select q from QuestionAttempt q join fetch q.attempt a join fetch a.user join fetch a.examInstance i join fetch i.batch join fetch q.examInstanceQuestion iq join fetch iq.question qu join fetch qu.questionType where q.centreId = :centreId and q.questionAttemptId = :id and qu.questionType.code = 'WRITTEN' and a.status in (com.learnium.model.exam.ExamAttemptStatus.SUBMITTED, com.learnium.model.exam.ExamAttemptStatus.AUTO_SUBMITTED) and (:admin = true or exists (select m from BatchMembership m where m.centreId = :centreId and m.batch = i.batch and m.user.userId = :userId and m.memberRole = com.learnium.model.user.MemberRole.TEACHER and m.status = com.learnium.model.user.MembershipStatus.ACTIVE))")
    java.util.Optional<QuestionAttempt> manageableWritten(UUID centreId, UUID userId, boolean admin, UUID id);

    /** Every answer of every attempt of one instance, for the teacher attempt list (one query, grouped in memory). */
    @Query("select q from QuestionAttempt q join fetch q.examInstanceQuestion iq join q.attempt a"
            + " where q.centreId = :centreId and a.examInstance.examInstanceId = :instanceId")
    List<QuestionAttempt> findByCentreIdAndInstanceId(
            @Param("centreId") UUID centreId, @Param("instanceId") UUID instanceId);

    /** Centre-scoped: a foreign centre's attempt id returns nothing, never its answers. */
    @Query(
            "select qa from QuestionAttempt qa"
                    + " where qa.centreId = :centreId and qa.attempt.attemptId = :attemptId")
    List<QuestionAttempt> findByCentreIdAndAttemptId(
            @Param("centreId") UUID centreId, @Param("attemptId") UUID attemptId);

    @Query("select qa from QuestionAttempt qa where qa.centreId = :centreId and qa.attempt.attemptId = :attemptId"
            + " and qa.examInstanceQuestion.examInstanceQuestionId = :itemId")
    java.util.Optional<QuestionAttempt> findSaved(UUID centreId, UUID attemptId, UUID itemId);
}
