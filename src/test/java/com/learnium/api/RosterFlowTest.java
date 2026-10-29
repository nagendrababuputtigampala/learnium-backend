package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learnium.model.common.UserStatus;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.master.Subject;
import com.learnium.model.user.BatchMembership;
import com.learnium.model.user.Centre;
import com.learnium.model.user.MembershipStatus;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.master.GradeRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.master.SubjectRepository;
import com.learnium.repository.user.BatchMembershipRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.testsupport.AuthFixtures;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Roster management over HTTP: add/bulk/reset/deactivate + access guards. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RosterFlowTest {

    private static final String PASSWORD = "Passw0rd1";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CentreRepository centreRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private BatchMembershipRepository batchMembershipRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private GradeSubjectRepository gradeSubjectRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Centre centreA;
    private UserAccount teacherA2;
    private UUID batchId;
    private String adminToken;
    private String teacherToken;

    @BeforeEach
    void setUp() throws Exception {
        centreA = AuthFixtures.createCentre(centreRepository, "Alpha Centre");
        Centre centreB = AuthFixtures.createCentre(centreRepository, "Beta Centre");
        AuthFixtures.createStaff(
                userAccountRepository, passwordEncoder, centreA, UserRole.CENTRE_ADMIN,
                "admin@alpha.test", "Ann Admin", PASSWORD, UserStatus.ACTIVE);
        UserAccount teacherA1 =
                AuthFixtures.createStaff(
                        userAccountRepository, passwordEncoder, centreA, UserRole.TEACHER,
                        "tess@alpha.test", "Tess Teacher", PASSWORD, UserStatus.ACTIVE);
        teacherA2 =
                AuthFixtures.createStaff(
                        userAccountRepository, passwordEncoder, centreA, UserRole.TEACHER,
                        "tim@alpha.test", "Tim Teacher", PASSWORD, UserStatus.ACTIVE);
        AuthFixtures.createStaff(
                userAccountRepository, passwordEncoder, centreB, UserRole.CENTRE_ADMIN,
                "admin@beta.test", "Bob Beta", PASSWORD, UserStatus.ACTIVE);

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
        GradeSubject gradeSubject = gradeSubjectRepository.save(gs);

        adminToken = tokenFor("admin@alpha.test");
        String created =
                mockMvc.perform(
                                post("/api/v1/admin/batches")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"name\":\"Batch B\",\"gradeSubjectId\":\""
                                                        + gradeSubject.getGradeSubjectId()
                                                        + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        batchId = UUID.fromString(objectMapper.readTree(created).get("batchId").asString());
        mockMvc.perform(
                        put("/api/v1/admin/batches/" + batchId + "/teacher")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"" + teacherA1.getUserId() + "\"}"))
                .andExpect(status().isOk());
        teacherToken = tokenFor("tess@alpha.test");
    }

    private String tokenFor(String email) throws Exception {
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

    private JsonNode addStudent(String token, String fullName) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/batches/" + batchId + "/students")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"fullName\":\"" + fullName + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    void addStudentReturnsPinOnceAndListsOnRoster() throws Exception {
        JsonNode added = addStudent(adminToken, "  Aisha Khan  ");
        assertThat(added.get("fullName").asString()).isEqualTo("Aisha Khan");
        assertThat(added.get("pin").asString()).matches("\\d{4}");
        assertThat(added.get("membershipId").asString()).isNotBlank();
        assertThat(added.get("userId").asString()).isNotBlank();

        mockMvc.perform(
                        get("/api/v1/batches/" + batchId + "/students")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fullName").value("Aisha Khan"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].pinLocked").value(false));

        // The batch DTO counts the new ACTIVE student.
        mockMvc.perform(get("/api/v1/batches").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].studentCount").value(1));
    }

    @Test
    void assignedTeacherCanManageRosterUnassignedTeacherCannot() throws Exception {
        addStudent(teacherToken, "Ravi Rao");

        String timToken = tokenFor("tim@alpha.test");
        mockMvc.perform(
                        get("/api/v1/batches/" + batchId + "/students")
                                .header("Authorization", "Bearer " + timToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-003"));
        mockMvc.perform(
                        post("/api/v1/batches/" + batchId + "/students")
                                .header("Authorization", "Bearer " + timToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fullName\":\"Sneaky\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-003"));
    }

    @Test
    void studentRoleCannotTouchRoster() throws Exception {
        mockMvc.perform(
                        get("/api/v1/batches/" + batchId + "/students")
                                .with(user("someone").roles("STUDENT")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-003"));
    }

    @Test
    void foreignBatchIsInvisible() throws Exception {
        UUID foreignBatch = UUID.randomUUID();
        mockMvc.perform(
                        get("/api/v1/batches/" + foreignBatch + "/students")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-CTR-001"));
    }

    @Test
    void bulkAddCreatesInRequestOrderAndRejectsBlanks() throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/batches/" + batchId + "/students/bulk")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"fullNames\":[\" Zara Ahmed \",\"Ben Osei\",\"Cleo Park\"]}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode added = objectMapper.readTree(body);
        assertThat(added.size()).isEqualTo(3);
        assertThat(added.get(0).get("fullName").asString()).isEqualTo("Zara Ahmed");
        assertThat(added.get(1).get("fullName").asString()).isEqualTo("Ben Osei");
        assertThat(added.get(2).get("fullName").asString()).isEqualTo("Cleo Park");

        mockMvc.perform(
                        post("/api/v1/batches/" + batchId + "/students/bulk")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fullNames\":[\"Fine Name\",\"  \"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-VAL-001"));
        mockMvc.perform(
                        post("/api/v1/batches/" + batchId + "/students/bulk")
                                .header("Authorization", "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"fullNames\":[]}"))
                .andExpect(status().isBadRequest());
        // The rejected bulk call added nobody (single transaction).
        mockMvc.perform(
                        get("/api/v1/batches/" + batchId + "/students")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void resetPinClearsLockAndAttempts() throws Exception {
        JsonNode added = addStudent(adminToken, "Locky Singh");
        UUID membershipId = UUID.fromString(added.get("membershipId").asString());
        BatchMembership membership =
                batchMembershipRepository
                        .findByCentreIdAndMembershipId(centreA.getCentreId(), membershipId)
                        .orElseThrow();
        membership.setPinFailedAttempts(4);
        membership.setPinLocked(true);
        batchMembershipRepository.save(membership);

        String body =
                mockMvc.perform(
                                post(
                                                "/api/v1/batches/"
                                                        + batchId
                                                        + "/students/"
                                                        + membershipId
                                                        + "/reset-pin")
                                        .header("Authorization", "Bearer " + teacherToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(objectMapper.readTree(body).get("pin").asString()).matches("\\d{4}");

        BatchMembership reloaded =
                batchMembershipRepository
                        .findByCentreIdAndMembershipId(centreA.getCentreId(), membershipId)
                        .orElseThrow();
        assertThat(reloaded.isPinLocked()).isFalse();
        assertThat(reloaded.getPinFailedAttempts()).isZero();
    }

    @Test
    void resetPinForUnknownOrForeignMembershipReturns404() throws Exception {
        mockMvc.perform(
                        post(
                                        "/api/v1/batches/"
                                                + batchId
                                                + "/students/"
                                                + UUID.randomUUID()
                                                + "/reset-pin")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-CTR-002"));
    }

    @Test
    void resetPinForMembershipOfAnotherBatchReturns404() throws Exception {
        JsonNode added = addStudent(adminToken, "Other Batch Otto");
        UUID membershipId = UUID.fromString(added.get("membershipId").asString());
        // A second batch in the same centre: the membership does not belong to it.
        String created =
                mockMvc.perform(
                                post("/api/v1/admin/batches")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"name\":\"Batch C\",\"gradeSubjectId\":\""
                                                        + batchRepositoryGradeSubjectId()
                                                        + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String otherBatchId = objectMapper.readTree(created).get("batchId").asString();

        mockMvc.perform(
                        post("/api/v1/batches/" + otherBatchId + "/students/" + membershipId + "/reset-pin")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-CTR-002"));
    }

    @Test
    void resetPinForTeacherMembershipReturns404() throws Exception {
        UUID teacherMembershipId =
                batchMembershipRepository
                        .findByCentreIdAndBatchIdAndMemberRole(
                                centreA.getCentreId(), batchId, com.learnium.model.user.MemberRole.TEACHER)
                        .get(0)
                        .getMembershipId();

        mockMvc.perform(
                        post("/api/v1/batches/" + batchId + "/students/" + teacherMembershipId + "/reset-pin")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-CTR-002"));
    }

    private String batchRepositoryGradeSubjectId() {
        return batchMembershipRepository
                .findByCentreIdAndBatchIdAndMemberRole(
                        centreA.getCentreId(), batchId, com.learnium.model.user.MemberRole.TEACHER)
                .get(0)
                .getBatch()
                .getGradeSubject()
                .getGradeSubjectId()
                .toString();
    }

    @Test
    void deactivateStudentMarksMembershipInactive() throws Exception {
        JsonNode added = addStudent(adminToken, "Leaving Lisa");
        UUID membershipId = UUID.fromString(added.get("membershipId").asString());

        mockMvc.perform(
                        delete("/api/v1/batches/" + batchId + "/students/" + membershipId)
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/api/v1/batches/" + batchId + "/students")
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("INACTIVE"));

        BatchMembership membership =
                batchMembershipRepository
                        .findByCentreIdAndMembershipId(centreA.getCentreId(), membershipId)
                        .orElseThrow();
        assertThat(membership.getStatus()).isEqualTo(MembershipStatus.INACTIVE);
    }
}
