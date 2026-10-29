package com.learnium.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learnium.model.common.UserStatus;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.master.Subject;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserRole;
import com.learnium.repository.master.GradeRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.master.SubjectRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.testsupport.AuthFixtures;
import com.learnium.testsupport.CommittedDataCleanup;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * OVER_LIMIT at the API boundary, driven by tiny test caps instead of
 * genuinely huge chapters.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(
        properties = {
            "learnium.extraction.max-extracted-chars=100",
            "learnium.extraction.max-input-tokens=1000000"
        })
class MaterialFlowLimitsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CentreRepository centreRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private GradeSubjectRepository gradeSubjectRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        CommittedDataCleanup.clearAll(jdbcTemplate);
    }

    @Test
    void pasteBeyondTheCapIsStoredTruncatedAndMarkedOverLimit() throws Exception {
        Centre centre = AuthFixtures.createCentre(centreRepository, "Limits Centre");
        AuthFixtures.createStaff(
                userAccountRepository, passwordEncoder, centre, UserRole.TEACHER,
                "lee@limits.test", "Lee Limits", "Passw0rd1", UserStatus.ACTIVE);
        Grade grade = new Grade();
        grade.setCode("G6");
        grade.setName("Grade 6");
        grade = gradeRepository.save(grade);
        Subject subject = new Subject();
        subject.setSubjectName("Science");
        subject = subjectRepository.save(subject);
        GradeSubject gs = new GradeSubject();
        gs.setGrade(grade);
        gs.setSubject(subject);
        gs = gradeSubjectRepository.save(gs);

        String loginBody =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\"lee@limits.test\",\"password\":\"Passw0rd1\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String token = objectMapper.readTree(loginBody).get("accessToken").asString();

        mockMvc.perform(
                        post("/api/v1/materials/text")
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                Map.of(
                                                        "title",
                                                        "Long chapter",
                                                        "gradeSubjectId",
                                                        gs.getGradeSubjectId().toString(),
                                                        "text",
                                                        "word ".repeat(200)))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extractionStatus").value("OVER_LIMIT"))
                .andExpect(jsonPath("$.extractedChars").value(100));
    }
}
