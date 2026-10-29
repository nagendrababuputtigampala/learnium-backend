package com.learnium.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Materials API payloads (M3): chapter uploads and pasted text. */
public final class MaterialDtos {

    private MaterialDtos() {}

    /**
     * One material as list/detail views present it. {@code deduplicated} is
     * true only on the create response when the same uploader had already
     * uploaded identical content — the existing material is returned instead
     * of storing a second copy.
     */
    public record MaterialDto(
            UUID materialId,
            String title,
            UUID gradeSubjectId,
            String gradeCode,
            String gradeName,
            String subjectName,
            String sourceType,
            String extractionStatus,
            Integer extractedChars,
            String contentHash,
            boolean deduplicated,
            Instant createdAt) {}

    /** Detail view: the summary plus the extracted text the teacher can inspect. */
    public record MaterialDetailDto(
            UUID materialId,
            String title,
            UUID gradeSubjectId,
            String gradeCode,
            String gradeName,
            String subjectName,
            String sourceType,
            String extractionStatus,
            Integer extractedChars,
            String contentHash,
            String extractedText,
            Instant createdAt) {}

    public record CreateMaterialTextRequest(
            @NotBlank(message = "must not be blank")
                    @Size(max = 200, message = "must be at most 200 characters")
                    String title,
            @NotNull(message = "must not be null") UUID gradeSubjectId,
            @NotBlank(message = "must not be blank")
                    @Size(max = 400_000, message = "must be at most 400000 characters")
                    String text) {}
}
