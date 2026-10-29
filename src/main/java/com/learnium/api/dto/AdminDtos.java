package com.learnium.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.hibernate.validator.constraints.URL;

/** Request/response records for centre admin, platform onboarding and catalogues. */
public final class AdminDtos {

    private AdminDtos() {}

    public record CentreDto(UUID centreId, String name, String logoUrl, String contactEmail) {}

    public record UpdateCentreRequest(
            @NotBlank(message = "must not be blank") String name,
            @URL(message = "must be a valid URL") String logoUrl,
            @Email(message = "must be a valid email") String contactEmail) {}

    public record CreateCentreRequest(
            @NotBlank(message = "must not be blank") String name,
            @NotBlank(message = "must not be blank") @Email(message = "must be a valid email")
                    String adminEmail,
            @NotBlank(message = "must not be blank") String adminDisplayName) {}

    public record CreateCentreResponse(UUID centreId, UUID adminUserId) {}

    public record GradeSubjectDto(
            UUID gradeSubjectId,
            String gradeCode,
            String gradeName,
            UUID subjectId,
            String subjectName,
            String label) {}

    public record TopicDto(UUID topicId, String name) {}

    public record PublicCentreDto(UUID centreId, String name) {}

    public record PublicBatchDto(UUID batchId, String name) {}
}
