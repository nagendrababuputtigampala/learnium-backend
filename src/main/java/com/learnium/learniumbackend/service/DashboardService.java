package com.learnium.learniumbackend.service;

import com.learnium.learniumbackend.dto.DashboardResponse;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    public DashboardResponse getDashboardForUser(String idToken) {
        // In production, decode the token, fetch userId, and query DB for real data.
        // Here, return mock data as per the required response structure.
        return DashboardResponse.builder()
                .subjectProgress(java.util.Arrays.asList(
                        DashboardResponse.SubjectProgress.builder().subject("Math").score(85).total(100).icon("\uD83D\uDCCA").build(),
                        DashboardResponse.SubjectProgress.builder().subject("Physics").score(72).total(100).icon("\u269B\uFE0F").build(),
                        DashboardResponse.SubjectProgress.builder().subject("Chemistry").score(0).total(100).icon("\uD83E\uDDEA").comingSoon(true).build()
                ))
                .recentActivity(java.util.Arrays.asList(
                        DashboardResponse.RecentActivity.builder().id(1).title("Algebra Quiz").subject("Math").score(95).date("2 hours ago").build(),
                        DashboardResponse.RecentActivity.builder().id(2).title("Newton's Laws").subject("Physics").score(88).date("1 day ago").build()
                ))
                .performanceData(java.util.Arrays.asList(
                        DashboardResponse.PerformanceData.builder().month("Aug").math(65).physics(60).build(),
                        DashboardResponse.PerformanceData.builder().month("Sep").math(72).physics(65).build(),
                        DashboardResponse.PerformanceData.builder().month("Oct").math(78).physics(70).build()
                ))
                .skillsData(java.util.Arrays.asList(
                        DashboardResponse.SkillData.builder().skill("Problem Solving").value(85).build(),
                        DashboardResponse.SkillData.builder().skill("Critical Thinking").value(78).build()
                ))
                .achievements(java.util.Arrays.asList(
                        DashboardResponse.Achievement.builder().id(1).name("Quick Learner").description("Complete 5 quizzes in a day").icon("\u26A1").unlocked(true).build(),
                        DashboardResponse.Achievement.builder().id(2).name("Math Wizard").description("Score 90+ in 10 math quizzes").icon("\uD83E\uDDD9").unlocked(true).build()
                ))
                .build();
    }
}

