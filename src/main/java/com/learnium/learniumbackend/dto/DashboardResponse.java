package com.learnium.learniumbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {
    private List<SubjectProgress> subjectProgress;
    private List<RecentActivity> recentActivity;
    private List<PerformanceData> performanceData;
    private List<SkillData> skillsData;
    private List<Achievement> achievements;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SubjectProgress {
        private String subject;
        private int score;
        private int total;
        private String icon;
        private Boolean comingSoon;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentActivity {
        private int id;
        private String title;
        private String subject;
        private int score;
        private String date;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PerformanceData {
        private String month;
        private Integer math;
        private Integer physics;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SkillData {
        private String skill;
        private int value;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Achievement {
        private int id;
        private String name;
        private String description;
        private String icon;
        private boolean unlocked;
    }
}

