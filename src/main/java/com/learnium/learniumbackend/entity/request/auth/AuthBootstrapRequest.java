package com.learnium.learniumbackend.entity.request.auth;

import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import jakarta.validation.Valid;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthBootstrapRequest {

    @Valid
    private Client client;

    @Valid
    private Student student;

    @Valid
    private Consents consents;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Client {
        private Platform platform;
        private String appVersion;
        private String deviceId;
        private String timezone;
        private String locale;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Student {
        private String gradeCode;
        private String studentName;
        private LocalDate dob;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Consents {
        private Boolean termsAccepted;
        private Instant termsAcceptedAt;
        private Boolean marketingOptIn;
    }

    public enum Platform { WEB, ANDROID, IOS }
}