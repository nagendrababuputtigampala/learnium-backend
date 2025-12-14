package com.learnium.learniumbackend.dto;

import lombok.*;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileData {
    private UUID id;
    private String firebaseUid;
    private String email;
    private String displayName;
    private String role;
    private Integer grade;
    private String school;
    private String bio;
    private String avatar;
    private String phone;
    private String city;
    private String state;
    private String country;
    private int totalPoints;
    private int currentStreak;
    private int longestStreak;
    private int problemsSolved;
    private int badgesEarned;
    private List<SkillDTO> skills;
    private List<CertificateDTO> certificates;
    private List<EducationDTO> education;
    private List<LinkDTO> links;
    private boolean isProfileComplete;
    private int completionPercentage;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkillDTO {
        private String name;
        private int level;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CertificateDTO {
        private String id;
        private String title;
        private String issuer;
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EducationDTO {
        private String id;
        private String institution;
        private String grade;
        private String startDate;
        private boolean current;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LinkDTO {
        private String id;
        private String platform;
        private String url;
    }
}

