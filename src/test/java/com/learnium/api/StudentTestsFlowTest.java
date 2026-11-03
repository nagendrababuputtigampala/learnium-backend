package com.learnium.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.learnium.model.common.UserStatus;
import com.learnium.model.exam.*;
import com.learnium.model.user.*;
import com.learnium.repository.exam.ExamAttemptRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class StudentTestsFlowTest extends PublishingFixtures {
    @Autowired ExamAttemptRepository attempts;

    @Test void assignedStudentSeesOnlyOwnCentreScopedTestsAndStaffAreForbidden() throws Exception {
        var published = publish(create().path("id").asText(), publishBody());
        mvc.perform(get("/api/v1/student/tests").with(as(student))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].instanceId").value(published.path("id").asText()))
                .andExpect(jsonPath("$[0].state").value("AVAILABLE"))
                .andExpect(jsonPath("$[0].attemptsUsed").value(0));
        mvc.perform(get("/api/v1/student/tests").with(as(owner))).andExpect(status().isForbidden());
        UserAccount unassigned = user(centre, UserRole.STUDENT); unassigned.setStatus(UserStatus.ACTIVE); users.save(unassigned);
        mvc.perform(get("/api/v1/student/tests").with(as(unassigned))).andExpect(status().isOk()).andExpect(content().json("[]"));
        UserAccount foreignStudent = user(otherCentre, UserRole.STUDENT); users.save(foreignStudent);
        mvc.perform(get("/api/v1/student/tests").with(as(foreignStudent))).andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void submittedLatestAttemptReturnsScoreAndPendingState() throws Exception {
        var published = publish(create().path("id").asText(), publishBody());
        ExamAttempt attempt = new ExamAttempt(); attempt.setCentreId(centre.getCentreId());
        attempt.setExamInstance(instances.findById(UUID.fromString(published.path("id").asText())).orElseThrow());
        attempt.setUser(student); attempt.setAttemptNo(1); attempt.setStatus(ExamAttemptStatus.SUBMITTED);
        attempt.setStartedAt(Instant.now().minusSeconds(60)); attempt.setSubmittedAt(Instant.now());
        attempt.setTotalScore(new BigDecimal("2.50")); attempts.saveAndFlush(attempt);
        mvc.perform(get("/api/v1/student/tests").with(as(student))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].latestAttemptStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$[0].totalSoFar").value(2.5));
    }
}
