package com.learnium.api.dto;

import jakarta.validation.constraints.NotBlank;
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
            UUID membershipId, UUID userId, String fullName, String status, boolean pinLocked) {}

    public record AddStudentRequest(@NotBlank(message = "must not be blank") String fullName) {}

    public record BulkAddStudentsRequest(
            @NotEmpty(message = "must not be empty")
                    List<@NotBlank(message = "must not be blank") String> fullNames) {}

    /** The raw PIN is returned exactly once, in this response only. */
    public record AddStudentResponse(UUID membershipId, UUID userId, String fullName, String pin) {}

    public record ResetPinResponse(String pin) {}
}
