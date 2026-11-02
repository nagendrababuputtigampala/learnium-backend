package com.learnium.repository.content;

import com.learnium.model.content.Question;
import com.learnium.model.content.QuestionStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from Question q where q.questionId = :id and q.centre.centreId = :centreId")
    java.util.Optional<Question> findForReview(UUID id, UUID centreId);

    @Query("""
            select q.questionId as questionId, qt.code as type, q.status as status,
              gs.gradeSubjectId as gradeSubjectId, g.name as gradeName, s.subjectName as subjectName,
              t.topicId as topicId, t.topicName as topicName, q.difficulty as difficulty,
              q.payload as payload, q.correctRef as correctRef, (q.version - 1) as revision, q.version as version,
              q.createdBy as createdBy, q.createdAt as createdAt, q.approvedBy as approvedBy,
              q.approvedAt as approvedAt, q.sourceQuestionId as sourceQuestionId,
              m.materialId as materialId, m.title as materialTitle,
              q.generationProvider as provider, q.generatedByModel as model,
              q.promptVersion as promptVersion, q.generationJobId as generationJobId
            from Question q join q.questionType qt join q.topic t join t.gradeSubject gs
              join gs.grade g join gs.subject s left join q.material m
            where q.centre.centreId = :centreId and q.active = true
              and (q.status = com.learnium.model.content.QuestionStatus.APPROVED
                or :admin = true or q.createdBy = :userId)
              and (:id is null or q.questionId = :id)
              and (:status is null or q.status = :status)
              and (:materialId is null or m.materialId = :materialId)
              and (:gradeSubjectId is null or gs.gradeSubjectId = :gradeSubjectId)
              and (:gradeId is null or g.gradeId = :gradeId)
              and (:subjectId is null or s.subjectId = :subjectId)
              and (:topicId is null or t.topicId = :topicId)
              and (:type is null or qt.code = :type)
              and (:difficulty is null or q.difficulty = :difficulty)
            """)
    Page<QuestionSummary> search(UUID centreId, UUID userId, boolean admin, UUID id,
            QuestionStatus status, UUID materialId, UUID gradeSubjectId, UUID gradeId,
            UUID subjectId, UUID topicId, String type, Short difficulty, Pageable pageable);

    /** Centre-scoped review queue: one centre's questions in one approval state, paged. */
    @Query(
            "select q from Question q"
                    + " where q.centre.centreId = :centreId and q.status = :status")
    Page<Question> findByCentreIdAndStatus(
            @Param("centreId") UUID centreId,
            @Param("status") QuestionStatus status,
            Pageable pageable);

    /** Draft preview for one material: that material's questions in this centre, paged. */
    Page<Question> findByCentreCentreIdAndMaterialMaterialId(
            UUID centreId, UUID materialId, Pageable pageable);

    /** How many questions one generation job has committed (exactly-once checks). */
    long countByGenerationJobId(UUID generationJobId);
}
