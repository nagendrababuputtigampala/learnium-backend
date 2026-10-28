package com.learnium.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;

/** Request/response records for the auth + account surface. */
public final class AuthDtos {

    private AuthDtos() {}

    /** The password rule, enforced everywhere a password is set. */
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$";

    public static final String PASSWORD_MESSAGE =
            "must be at least 8 characters and contain a letter and a digit";

    public record LoginRequest(
            @NotBlank(message = "must not be blank") @Email(message = "must be a valid email") String email,
            @NotBlank(message = "must not be blank") String password) {}

    public record RefreshRequest(@NotBlank(message = "must not be blank") String refreshToken) {}

    public record LogoutRequest(@NotBlank(message = "must not be blank") String refreshToken) {}

    public record ForgotPasswordRequest(
            @NotBlank(message = "must not be blank") @Email(message = "must be a valid email") String email) {}

    public record ResetPasswordRequest(
            @NotBlank(message = "must not be blank") String token,
            @NotBlank(message = "must not be blank")
                    @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
                    String newPassword) {}

    public record AcceptInviteRequest(
            @NotBlank(message = "must not be blank") String token,
            @NotBlank(message = "must not be blank")
                    @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
                    String newPassword) {}

    public record UserSummary(
            UUID userId, String displayName, String email, String role, UUID centreId, String centreName) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresInSeconds,
            UserSummary user) {}

    public record MeResponse(
            UUID userId, String displayName, String email, String role, UUID centreId, String centreName) {}

    public record CreateTeacherRequest(
            @NotBlank(message = "must not be blank") @Email(message = "must be a valid email") String email,
            @NotBlank(message = "must not be blank") String displayName) {}

    public record TeacherDto(
            UUID userId, String email, String displayName, boolean active, Instant createdAt) {}

    public record StudentSessionRequest(
            @NotNull(message = "must not be null") UUID batchId,
            @NotBlank(message = "must not be blank") String fullName,
            @NotBlank(message = "must not be blank") String pin) {}

    public record StudentSummary(
            UUID userId,
            String displayName,
            UUID membershipId,
            UUID batchId,
            String batchName,
            UUID centreId) {}

    public record StudentSessionResponse(
            String accessToken, String tokenType, long expiresInSeconds, StudentSummary student) {}
}
