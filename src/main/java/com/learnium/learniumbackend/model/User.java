package com.learnium.learniumbackend.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private java.util.UUID id;

    @Column(name = "firebase_uid", unique = true, nullable = false)
    private String firebaseUid;

    @Column(name = "email", columnDefinition = "citext")
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "grade_level", nullable = false)
    private Integer gradeLevel;

    @Column(name = "onboarding_done", nullable = false)
    private boolean onboardingDone = false;

    @Column(name = "role", nullable = false)
    private String role = "STUDENT";

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "school")
    private String school;

    @Column(name = "bio")
    private String bio;

    @Column(name = "avatar")
    private String avatar;

    @Column(name = "phone")
    private String phone;

    @Column(name = "city")
    private String city;

    @Column(name = "state")
    private String state;

    @Column(name = "country")
    private String country;

    @Column(name = "total_points", nullable = false)
    private int totalPoints = 0;

    @Column(name = "current_streak", nullable = false)
    private int currentStreak = 0;

    @Column(name = "longest_streak", nullable = false)
    private int longestStreak = 0;

    @Column(name = "problems_solved", nullable = false)
    private int problemsSolved = 0;

    @Column(name = "badges_earned", nullable = false)
    private int badgesEarned = 0;

    @Column(name = "is_profile_complete", nullable = false)
    private boolean isProfileComplete = false;

    @Column(name = "completion_percentage", nullable = false)
    private int completionPercentage = 0;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
