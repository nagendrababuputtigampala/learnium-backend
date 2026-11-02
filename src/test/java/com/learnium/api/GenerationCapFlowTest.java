package com.learnium.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learnium.model.common.UserStatus;
import com.learnium.model.content.Topic;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.master.Subject;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserRole;
import com.learnium.repository.content.TopicRepository;
import com.learnium.repository.master.GradeRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.master.SubjectRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.testsupport.AuthFixtures;
import com.learnium.testsupport.CommittedDataCleanup;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Daily generation caps (LRN-GEN-004) with tiny configured limits: the
 * per-teacher cap trips on a teacher's own third job; the per-centre cap
 * trips on the centre's fourth job overall, whoever submits it.
 */
@SpringBootTest(
        properties = {
            "learnium.generation.max-jobs-per-teacher-per-day=2",
            "learnium.generation.max-jobs-per-centre-per-day=3"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GenerationCapFlowTest {

    private static final String PASSWORD = "Passw0rd1";
    private static final String PROSE =
            "Fractions represent parts of a whole. When the numerator and the denominator "
                    + "share a common factor, the fraction can be simplified by dividing both "
                    + "by that factor. Equivalent fractions name the same value in different "
                    + "forms, which is why one half equals two quarters and three sixths. ";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CentreRepository centreRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private GradeSubjectRepository gradeSubjectRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private GradeSubject gradeSubject;
    private Topic topic;
    private String teacherToken;
    private String teacher2Token;
    private UUID materialA;
    private UUID materialB;

    @AfterEach
    void cleanUp() {
        CommittedDataCleanup.clearAll(jdbcTemplate);
    }

    @BeforeEach
    void setUp() throws Exception {
        Centre centre = AuthFixtures.createCentre(centreRepository, "Cap Centre");
        AuthFixtures.createStaff(
                userAccountRepository, passwordEncoder, centre, UserRole.TEACHER,
                "tess@cap.test", "Tess Teacher", PASSWORD, UserStatus.ACTIVE);
        AuthFixtures.createStaff(
                userAccountRepository, passwordEncoder, centre, UserRole.TEACHER,
                "tim@cap.test", "Tim Teacher", PASSWORD, UserStatus.ACTIVE);
        Grade grade = new Grade();
        grade.setCode("G8");
        grade.setName("Grade 8");
        grade = gradeRepository.save(grade);
        Subject subject = new Subject();
        subject.setSubjectName("Mathematics");
        subject = subjectRepository.save(subject);
        GradeSubject gs = new GradeSubject();
        gs.setGrade(grade);
        gs.setSubject(subject);
        gradeSubject = gradeSubjectRepository.save(gs);
        Topic fractions = new Topic();
        fractions.setGradeSubject(gradeSubject);
        fractions.setTopicName("Fractions");
        topic = topicRepository.save(fractions);

        teacherToken = login("tess@cap.test");
        teacher2Token = login("tim@cap.test");
        materialA = pasteMaterial(teacherToken, "Chapter A");
        materialB = pasteMaterial(teacher2Token, "Chapter B");
    }

    @Test
    void teacherCapThenCentreCap() throws Exception {
        generate(teacherToken, materialA).andExpect(status().isAccepted());
        generate(teacherToken, materialA).andExpect(status().isAccepted());
        // Teacher cap (2/day): the same teacher's third job is refused...
        generate(teacherToken, materialA)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LRN-GEN-004"));
        // ...while a colleague's first job still fits under the centre cap (3/day).
        generate(teacher2Token, materialB).andExpect(status().isAccepted());
        // Centre cap reached: the colleague's second job is refused too.
        generate(teacher2Token, materialB)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LRN-GEN-004"));
    }

    private org.springframework.test.web.servlet.ResultActions generate(String token, UUID materialId)
            throws Exception {
        String body =
                "{\"topicId\":\"" + topic.getTopicId() + "\",\"counts\":{\"MCQ\":1}}";
        return mockMvc.perform(
                post("/api/v1/materials/" + materialId + "/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private UUID pasteMaterial(String token, String title) throws Exception {
        String body =
                objectMapper.writeValueAsString(
                        Map.of(
                                "title", title,
                                "gradeSubjectId", gradeSubject.getGradeSubjectId().toString(),
                                "text", PROSE.repeat(3)));
        JsonNode created =
                objectMapper.readTree(
                        mockMvc.perform(
                                        post("/api/v1/materials/text")
                                                .header("Authorization", "Bearer " + token)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(body))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        return UUID.fromString(created.get("materialId").asString());
    }

    private String login(String email) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\""
                                                        + email
                                                        + "\",\"password\":\""
                                                        + PASSWORD
                                                        + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body).get("accessToken").asString();
    }
}
