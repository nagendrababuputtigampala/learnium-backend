package com.learnium.learniumbackend.entity.response;

import jakarta.persistence.Column;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class UserDetails {
    private String email;

    private String displayName;

    private String gradeName;

    private Integer gradeId;

    private boolean onboardingDone = false;

    private String role = "STUDENT";

    private String status = "ACTIVE";

    private String school;

    private String bio;

    private String avatar;

    private String phone;

    private String city;

    private String state;

    private String country;

    private int totalPoints = 0;

    private int currentStreak = 0;

    private int longestStreak = 0;

    private int problemsSolved = 0;

    private int badgesEarned = 0;

    private boolean isProfileComplete = false;

    private int completionPercentage = 0;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;

}
