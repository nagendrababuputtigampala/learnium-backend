package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learnium.model.common.UserStatus;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.master.Subject;
import com.learnium.model.user.Batch;
import com.learnium.model.user.BatchMembership;
import com.learnium.model.user.Centre;
import com.learnium.model.user.MemberRole;
import com.learnium.model.user.MembershipStatus;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.master.GradeRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.master.SubjectRepository;
import com.learnium.repository.user.BatchMembershipRepository;
import com.learnium.repository.user.BatchRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.testsupport.AuthFixtures;
import com.learnium.testsupport.CommittedDataCleanup;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The student door: batch + name + PIN. Covers the happy path, the generic
 * 401 (never revealing which part failed), attempt counting, the 5-strike
 * lock, the locked 403, reset-unlock, and same-name disambiguation by PIN.
 *
 * <p>Committed-state class (no wrapping test transaction): the PIN lockout
 * only exists if failed attempts are committed by separate requests, which
 * is exactly what a class-level {@code @Transactional} cannot observe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StudentSessionTest {

    private static final String PASSWORD = "Passw0rd1";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CentreRepository centreRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private BatchRepository batchRepository;
    @Autowired private BatchMembershipRepository batchMembershipRepository;
    @Autowired private GradeRepository gradeRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private GradeSubjectRepository gradeSubjectRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Centre centre;
    private Batch batch;
    private String adminToken;

    @AfterEach
    void cleanUp() {
        CommittedDataCleanup.clearAll(jdbcTemplate);
    }

    @BeforeEach
    void setUp() throws Exception {
        centre = AuthFixtures.createCentre(centreRepository, "Alpha Centre");
        AuthFixtures.createStaff(
                userAccountRepository, passwordEncoder, centre, UserRole.CENTRE_ADMIN,
                "admin@alpha.test", "Ann Admin", PASSWORD, UserStatus.ACTIVE);
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
        gradeSubjectRepository.save(gs);
        batch = new Batch();
        batch.setCentre(centre);
        batch.setGradeSubject(gs);
        batch.setName("Batch B");
        batch = batchRepository.save(batch);

        String body =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\"admin@alpha.test\",\"password\":\""
                                                        + PASSWORD
                                                        + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        adminToken = objectMapper.readTree(body).get("accessToken").asString();
    }

    /** Adds a student through the real roster API; returns {membershipId, pin}. */
    private JsonNode addStudent(String fullName) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/batches/" + batch.getBatchId() + "/students")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"fullName\":\"" + fullName + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body);
    }

    private ResultActions session(UUID batchId, String fullName, String pin) throws Exception {
        return mockMvc.perform(
                post("/api/v1/auth/student-session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"batchId\":\""
                                        + batchId
                                        + "\",\"fullName\":\""
                                        + fullName
                                        + "\",\"pin\":\""
                                        + pin
                                        + "\"}"));
    }

    private BatchMembership membership(UUID membershipId) {
        return batchMembershipRepository
                .findByCentreIdAndMembershipId(centre.getCentreId(), membershipId)
                .orElseThrow();
    }

    private String wrongPin(String actual) {
        return "0000".equals(actual) ? "9999" : "0000";
    }

    @Test
    void sessionSuccessIssuesStudentTokenThatWorksOnMe() throws Exception {
        JsonNode added = addStudent("Simran Kaur");
        String pin = added.get("pin").asString();

        // Name matching is trim + case-insensitive.
        String body =
                session(batch.getBatchId(), "  simran kaur ", pin)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode session = objectMapper.readTree(body);
        assertThat(session.get("tokenType").asString()).isEqualTo("Bearer");
        assertThat(session.get("expiresInSeconds").asLong()).isEqualTo(43_200L);
        assertThat(session.get("student").get("displayName").asString()).isEqualTo("Simran Kaur");
        assertThat(session.get("student").get("batchName").asString()).isEqualTo("Batch B");
        assertThat(session.get("student").get("centreId").asString())
                .isEqualTo(centre.getCentreId().toString());
        assertThat(session.get("student").get("membershipId").asString())
                .isEqualTo(added.get("membershipId").asString());

        String token = session.get("accessToken").asString();
        mockMvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andExpect(jsonPath("$.displayName").value("Simran Kaur"))
                .andExpect(jsonPath("$.email").value(org.hamcrest.Matchers.nullValue()));

        // A student token is not a staff token.
        mockMvc.perform(get("/api/v1/batches").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-003"));
        mockMvc.perform(get("/api/v1/admin/teachers").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownBatchReturns404() throws Exception {
        session(UUID.randomUUID(), "Anyone", "1234")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-CTR-001"));
    }

    @Test
    void unknownNameReturnsGeneric401() throws Exception {
        addStudent("Simran Kaur");
        session(batch.getBatchId(), "Nobody Here", "1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-007"));
    }

    @Test
    void wrongPinReturns401AndIncrementsAttempts() throws Exception {
        JsonNode added = addStudent("Simran Kaur");
        UUID membershipId = UUID.fromString(added.get("membershipId").asString());
        String bad = wrongPin(added.get("pin").asString());

        session(batch.getBatchId(), "Simran Kaur", bad)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-007"));
        assertThat(membership(membershipId).getPinFailedAttempts()).isEqualTo(1);
        assertThat(membership(membershipId).isPinLocked()).isFalse();
    }

    @Test
    void fifthFailureLocksAndLockedMembershipGets403EvenWithCorrectPin() throws Exception {
        JsonNode added = addStudent("Simran Kaur");
        UUID membershipId = UUID.fromString(added.get("membershipId").asString());
        String pin = added.get("pin").asString();
        String bad = wrongPin(pin);

        for (int i = 0; i < 5; i++) {
            session(batch.getBatchId(), "Simran Kaur", bad).andExpect(status().isUnauthorized());
        }
        assertThat(membership(membershipId).isPinLocked()).isTrue();
        assertThat(membership(membershipId).getPinFailedAttempts()).isEqualTo(5);

        session(batch.getBatchId(), "Simran Kaur", pin)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-006"));
    }

    @Test
    void resetPinUnlocksAndOldPinNoLongerWorks() throws Exception {
        JsonNode added = addStudent("Simran Kaur");
        UUID membershipId = UUID.fromString(added.get("membershipId").asString());
        String oldPin = added.get("pin").asString();
        BatchMembership locked = membership(membershipId);
        locked.setPinFailedAttempts(5);
        locked.setPinLocked(true);
        batchMembershipRepository.save(locked);

        String resetBody =
                mockMvc.perform(
                                post(
                                                "/api/v1/batches/"
                                                        + batch.getBatchId()
                                                        + "/students/"
                                                        + membershipId
                                                        + "/reset-pin")
                                        .header("Authorization", "Bearer " + adminToken))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String newPin = objectMapper.readTree(resetBody).get("pin").asString();

        session(batch.getBatchId(), "Simran Kaur", newPin).andExpect(status().isOk());
        if (!newPin.equals(oldPin)) {
            session(batch.getBatchId(), "Simran Kaur", oldPin)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("LRN-AUTH-007"));
        }
        assertThat(membership(membershipId).isPinLocked()).isFalse();
    }

    @Test
    void sameNameStudentsAreDisambiguatedByPin() throws Exception {
        JsonNode first = addStudent("Sam Lee");
        JsonNode second = addStudent("Sam Lee");
        UUID firstId = UUID.fromString(first.get("membershipId").asString());
        UUID secondId = UUID.fromString(second.get("membershipId").asString());
        // Pin the hashes to known values so the test never depends on random PINs.
        BatchMembership m1 = membership(firstId);
        m1.setPinHash(passwordEncoder.encode("1111"));
        batchMembershipRepository.save(m1);
        BatchMembership m2 = membership(secondId);
        m2.setPinHash(passwordEncoder.encode("2222"));
        batchMembershipRepository.save(m2);

        String body1 =
                session(batch.getBatchId(), "Sam Lee", "1111")
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(objectMapper.readTree(body1).get("student").get("membershipId").asString())
                .isEqualTo(firstId.toString());
        String body2 =
                session(batch.getBatchId(), "Sam Lee", "2222")
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(objectMapper.readTree(body2).get("student").get("membershipId").asString())
                .isEqualTo(secondId.toString());
    }

    @Test
    void lockedSameNameStudentDoesNotBlockTheUnlockedOne() throws Exception {
        JsonNode first = addStudent("Sam Lee");
        JsonNode second = addStudent("Sam Lee");
        UUID firstId = UUID.fromString(first.get("membershipId").asString());
        UUID secondId = UUID.fromString(second.get("membershipId").asString());
        BatchMembership m1 = membership(firstId);
        m1.setPinHash(passwordEncoder.encode("1111"));
        m1.setPinFailedAttempts(5);
        m1.setPinLocked(true);
        batchMembershipRepository.save(m1);
        BatchMembership m2 = membership(secondId);
        m2.setPinHash(passwordEncoder.encode("2222"));
        batchMembershipRepository.save(m2);

        // The unlocked Sam can still sign in with their own PIN.
        String body =
                session(batch.getBatchId(), "Sam Lee", "2222")
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(objectMapper.readTree(body).get("student").get("membershipId").asString())
                .isEqualTo(secondId.toString());
        // The locked Sam's correct PIN does not authenticate through the lock.
        session(batch.getBatchId(), "Sam Lee", "1111")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-007"));
    }

    @Test
    void deactivatedStudentCannotOpenSession() throws Exception {
        JsonNode added = addStudent("Simran Kaur");
        UUID membershipId = UUID.fromString(added.get("membershipId").asString());
        mockMvc.perform(
                        delete("/api/v1/batches/" + batch.getBatchId() + "/students/" + membershipId)
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        session(batch.getBatchId(), "Simran Kaur", added.get("pin").asString())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-007"));
    }

    @Test
    void membershipWithoutPinHashNeverAuthenticates() throws Exception {
        // A roster row created outside the API (no PIN ever issued).
        UserAccount user =
                AuthFixtures.createStaff(
                        userAccountRepository, passwordEncoder, centre, UserRole.STUDENT,
                        null, "No Pin Nora", null, UserStatus.ACTIVE);
        BatchMembership membership = new BatchMembership();
        membership.setCentreId(centre.getCentreId());
        membership.setBatch(batch);
        membership.setUser(user);
        membership.setMemberRole(MemberRole.STUDENT);
        membership.setStatus(MembershipStatus.ACTIVE);
        membership = batchMembershipRepository.save(membership);

        session(batch.getBatchId(), "No Pin Nora", "1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-007"));
        assertThat(membership(membership.getMembershipId()).getPinFailedAttempts()).isEqualTo(1);
    }
}
