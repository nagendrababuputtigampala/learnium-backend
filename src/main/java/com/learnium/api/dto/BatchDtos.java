package com.learnium.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/** Request/response records for batches and their rosters. */
public final class BatchDtos {

    private BatchDtos() {}

    public record CreateBatchRequest(
            @NotBlank(message = "must not be blank") String name,
            @NotNull(message = "must not be null") UUID gradeSubjectId) {}

    public record AssignTeacherRequest(@NotNull(message = "must not be null") UUID userId) {}

    public record BatchDto(
            UUID batchId,
            String name,
            UUID gradeSubjectId,
            String gradeLabel,
            UUID teacherUserId,
            String teacherName,
            long studentCount) {}

    public record RosterStudentDto(
            UUID membershipId, UUID userId, String displayName, String username,
            String contactEmail, String status, String inviteStatus) {}

    public record AddStudentRequest(
            @NotBlank(message = "must not be blank") String displayName,
            @Email(message = "must be a valid email") String contactEmail,
            String username,
            UUID userId) {}

    public record BulkAddStudentsRequest(
            @NotEmpty(message = "must not be empty")
                    List<@NotBlank(message = "must not be blank") String> fullNames) {}

    public record AddStudentResponse(
            UUID membershipId, UUID userId, String displayName, String username, String inviteStatus) {}

    public record RenameUsernameRequest(@NotBlank(message = "must not be blank") String username) {}

    public record UsernameCheckDto(boolean available) {}

    public record StudentLookupDto(UUID id, String displayName, String username) {}
}
