package com.learnium.learniumbackend.entity.response.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthBootstrapResponse {

    private Instant serverTime;

    @JsonProperty("isNewUser")
    private Boolean isNewUser;

    private AuthInfo auth;
    private UserDto user;
    private StudentProfileDto studentProfile;
    private PermissionsDto permissions;
    private AppDto app;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AuthInfo {
        private String provider;       // GOOGLE/EMAIL/APPLE
        private String firebaseUid;
        private boolean emailVerified;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class UserDto {
        private UUID userId;
        private String email;
        private String displayName;
        private String photoUrl;
        private String status;
        private Instant createdAt;
        private Instant lastLoginAt;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class StudentProfileDto {
        private UUID studentProfileId;
        private String studentName;
        private GradeDto grade;
        private String onboardingStatus;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class GradeDto {
        private UUID gradeId;
        private String code;
        private String name;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PermissionsDto {
        private List<String> roles;
        private List<String> scopes;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class AppDto {
        private Map<String, Boolean> featureFlags;
    }
}