package com.learnium.repository.content;

import com.learnium.model.content.Material;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialRepository extends JpaRepository<Material, UUID> {

    @Query("select m from Material m where m.centre.centreId = :centreId")
    List<Material> findByCentreId(@Param("centreId") UUID centreId);

    /** One visible (not soft-deleted) material inside its centre. */
    @Query(
            "select m from Material m"
                    + " join fetch m.gradeSubject gs join fetch gs.grade join fetch gs.subject"
                    + " where m.materialId = :materialId and m.centre.centreId = :centreId"
                    + " and m.deletedAt is null")
    Optional<Material> findVisibleById(
            @Param("materialId") UUID materialId, @Param("centreId") UUID centreId);

    /**
     * Same-uploader dedupe: identical extracted content already held by THIS
     * uploader in THIS centre. Never matches another teacher's material —
     * deduplication must not expose a private upload.
     */
    @Query(
            "select m from Material m"
                    + " join fetch m.gradeSubject gs join fetch gs.grade join fetch gs.subject"
                    + " where m.centre.centreId = :centreId and m.uploadedBy.userId = :uploaderId"
                    + " and m.contentHash = :contentHash and m.deletedAt is null")
    Optional<Material> findDedupeCandidate(
            @Param("centreId") UUID centreId,
            @Param("uploaderId") UUID uploaderId,
            @Param("contentHash") String contentHash);

    /** Admin view: every visible material in the centre. */
    @Query(
            "select m from Material m"
                    + " join fetch m.gradeSubject gs join fetch gs.grade join fetch gs.subject"
                    + " where m.centre.centreId = :centreId and m.deletedAt is null")
    Page<Material> findVisiblePageByCentreId(@Param("centreId") UUID centreId, Pageable pageable);

    /** Teacher view: only the teacher's own visible materials. */
    @Query(
            "select m from Material m"
                    + " join fetch m.gradeSubject gs join fetch gs.grade join fetch gs.subject"
                    + " where m.centre.centreId = :centreId and m.uploadedBy.userId = :uploaderId"
                    + " and m.deletedAt is null")
    Page<Material> findVisiblePageByCentreIdAndUploader(
            @Param("centreId") UUID centreId,
            @Param("uploaderId") UUID uploaderId,
            Pageable pageable);

    /**
     * Soft-deleted rows whose stored files are cleanup candidates. Physical
     * cleanup is deferred (a generation job may still reference the row);
     * branch 2 of M3 consumes this finder.
     */
    @Query("select m from Material m where m.deletedAt is not null")
    List<Material> findCleanupCandidates();
}
