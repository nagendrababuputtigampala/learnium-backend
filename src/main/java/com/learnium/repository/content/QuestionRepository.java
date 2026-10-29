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
