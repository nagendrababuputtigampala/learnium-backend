package com.learnium.model.content;

import com.learnium.model.common.AuditableEntity;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A chapter a teacher uploaded (PDF file or pasted text) — the source AI generation reads. */
@Entity
@Table(
        name = "materials",
        schema = "learnium",
        indexes = {
            @Index(name = "idx_materials_centre", columnList = "centre_id"),
            @Index(name = "idx_materials_grade_subject", columnList = "grade_subject_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class Material extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "centre_id", nullable = false)
    private Centre centre;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private UserAccount uploadedBy;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "grade_subject_id", nullable = false)
    private GradeSubject gradeSubject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic; // nullable until classified

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 10)
    private MaterialSourceType sourceType;

    /** Object-storage key of the uploaded file; null for pasted text. */
    @Column(name = "storage_key", length = 500)
    private String storageKey;

    @Column(name = "extracted_text", columnDefinition = "text")
    private String extractedText;

    /** SHA-256 hex of the stored extracted text; null until extraction ran. */
    @Column(name = "content_hash", length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", length = 24)
    private ExtractionStatus extractionStatus;

    /** Length of the stored extracted text (after any cap truncation). */
    @Column(name = "extracted_chars")
    private Integer extractedChars;

    /**
     * Soft-delete marker. A deleted material is hidden from every read path
     * and blocks any in-flight generation from persisting results; the row
     * and the stored file are physically cleaned up later, once nothing
     * references them.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
