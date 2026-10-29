package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learnium.mail.PayloadCipher;
import com.learnium.model.common.UserStatus;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.master.Subject;
import com.learnium.model.user.Batch;
import com.learnium.model.user.BatchMembership;
import com.learnium.model.user.Centre;
import com.learnium.model.user.MemberRole;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.auth.EmailOutboxRepository;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Admin-lite over HTTP: teachers, batches + assignment, centre profile,
 * catalogues.
 *
 * <p>Committed-state class (no wrapping test transaction): deactivation
 * revokes sessions in an independent transaction, which a class-level
 * {@code @Transactional} would render invisible to the requests under test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminFlowTest {

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
    @Autowired private EmailOutboxRepository emailOutboxRepository;
    @Autowired private PayloadCipher payloadCipher;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Centre centreA;
    private Centre centreB;
    private UserAccount adminA;
    private UserAccount teacherA1;
    private UserAccount teacherA2;
    private GradeSubject gradeSubject;

    @AfterEach
    void cleanUp() {
        CommittedDataCleanup.clearAll(jdbcTemplate);
    }

    @BeforeEach
    void setUp() {
        centreA = AuthFixtures.createCentre(centreRepository, "Alpha Centre");
        centreB = AuthFixtures.createCentre(centreRepository, "Beta Centre");
        adminA =
                AuthFixtures.createStaff(
                        userAccountRepository, passwordEncoder, centreA, UserRole.CENTRE_ADMIN,
                        "admin@alpha.test", "Ann Admin", PASSWORD, UserStatus.ACTIVE);
        teacherA1 =
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
        gradeSubject = gradeSubjectRepository.save(gs);
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

    private JsonNode createBatch(String adminToken, String name) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/admin/batches")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"name\":\""
                                                        + name
                                                        + "\",\"gradeSubjectId\":\""
                                                        + gradeSubject.getGradeSubjectId()
                                                        + "\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(body);
    }

    // ---------- teachers ----------

    @Test
    void createTeacherInvitesAndReturns201() throws Exception {
        String admin = tokenFor("admin@alpha.test");

        mockMvc.perform(
                        post("/api/v1/admin/teachers")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"new@alpha.test\",\"displayName\":\"New Teacher\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("new@alpha.test"))
                .andExpect(jsonPath("$.displayName").value("New Teacher"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void createTeacherWithExistingEmailInOwnCentreReturns409() throws Exception {
        String admin = tokenFor("admin@alpha.test");

        mockMvc.perform(
                        post("/api/v1/admin/teachers")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"tess@alpha.test\",\"displayName\":\"Duplicate\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LRN-USR-001"));
    }

    @Test
    void createTeacherWithEmailFromAnotherCentreReturns409() throws Exception {
        String admin = tokenFor("admin@alpha.test");

        mockMvc.perform(
                        post("/api/v1/admin/teachers")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"admin@beta.test\",\"displayName\":\"Duplicate\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LRN-USR-001"));
    }

    @Test
    void teacherCannotCreateTeacher() throws Exception {
        String teacher = tokenFor("tess@alpha.test");

        mockMvc.perform(
                        post("/api/v1/admin/teachers")
                                .header("Authorization", "Bearer " + teacher)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"x@alpha.test\",\"displayName\":\"X\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-003"));
    }

    @Test
    void listTeachersPagesWithinTheCentre() throws Exception {
        String admin = tokenFor("admin@alpha.test");

        mockMvc.perform(
                        get("/api/v1/admin/teachers?page=0&size=1")
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1));
        mockMvc.perform(
                        get("/api/v1/admin/teachers?page=1&size=1")
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void deactivateTeacherKillsSessionsAndBlocksLogin() throws Exception {
        String admin = tokenFor("admin@alpha.test");
        // Tess has a live session before deactivation.
        String tessLoginBody =
                mockMvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\"tess@alpha.test\",\"password\":\""
                                                        + PASSWORD
                                                        + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String tessRefresh = objectMapper.readTree(tessLoginBody).get("refreshToken").asString();

        mockMvc.perform(
                        delete("/api/v1/admin/teachers/" + teacherA1.getUserId())
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"refreshToken\":\"" + tessRefresh + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"email\":\"tess@alpha.test\",\"password\":\""
                                                + PASSWORD
                                                + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-005"));
    }

    @Test
    void deactivateUnknownOrForeignOrNonTeacherReturns404() throws Exception {
        String admin = tokenFor("admin@alpha.test");

        mockMvc.perform(
                        delete("/api/v1/admin/teachers/" + UUID.randomUUID())
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-USR-002"));
        // The admin's own account is not a teacher.
        mockMvc.perform(
                        delete("/api/v1/admin/teachers/" + adminA.getUserId())
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-USR-002"));
    }

    @Test
    void createTeacherNormalisesEmailCase() throws Exception {
        String admin = tokenFor("admin@alpha.test");

        mockMvc.perform(
                        post("/api/v1/admin/teachers")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"MixedCase@Alpha.test\",\"displayName\":\"Mix Case\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("mixedcase@alpha.test"))
                .andExpect(jsonPath("$.invited").value(true));

        // The same address in another case is the same account: 409.
        mockMvc.perform(
                        post("/api/v1/admin/teachers")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"MIXEDCASE@alpha.test\",\"displayName\":\"Dupe\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LRN-USR-001"));
    }

    /** Creates a teacher through the API and returns its user id. */
    private UUID inviteTeacherViaApi(String adminToken, String email) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/admin/teachers")
                                        .header("Authorization", "Bearer " + adminToken)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\""
                                                        + email
                                                        + "\",\"displayName\":\"Invited Teacher\"}"))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.invited").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("userId").asString());
    }

    /** The freshest invite token emailed to the given recipient, decrypted like the dispatcher does. */
    private String latestInviteTokenFor(String recipient) {
        return emailOutboxRepository.findByCentreId(centreA.getCentreId()).stream()
                .filter(row -> row.getRecipient().equals(recipient))
                .reduce((first, second) -> second)
                .map(row -> payloadCipher.decrypt(row.getModel()).get("token").asText())
                .orElseThrow();
    }

    @Test
    void resendInviteIssuesAFreshTokenAndKillsTheOldOne() throws Exception {
        String admin = tokenFor("admin@alpha.test");
        UUID teacherId = inviteTeacherViaApi(admin, "invitee@alpha.test");
        String firstToken = latestInviteTokenFor("invitee@alpha.test");

        mockMvc.perform(
                        post("/api/v1/admin/teachers/" + teacherId + "/resend-invite")
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(teacherId.toString()))
                .andExpect(jsonPath("$.email").value("invitee@alpha.test"));

        String secondToken = latestInviteTokenFor("invitee@alpha.test");
        assertThat(secondToken).isNotEqualTo(firstToken);

        // The superseded link is dead; only the fresh one sets the password.
        mockMvc.perform(
                        post("/api/v1/auth/accept-invite")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"token\":\""
                                                + firstToken
                                                + "\",\"newPassword\":\"First1pass\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-004"));
        mockMvc.perform(
                        post("/api/v1/auth/accept-invite")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"token\":\""
                                                + secondToken
                                                + "\",\"newPassword\":\"First1pass\"}"))
                .andExpect(status().isNoContent());

        // Accepted now: no longer flagged as invited, and resend is refused.
        String listBody =
                mockMvc.perform(
                                get("/api/v1/admin/teachers?page=0&size=20")
                                        .header("Authorization", "Bearer " + admin))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        for (JsonNode teacher : objectMapper.readTree(listBody).get("content")) {
            if (teacher.get("userId").asString().equals(teacherId.toString())) {
                assertThat(teacher.get("invited").asBoolean()).isFalse();
            }
        }
        mockMvc.perform(
                        post("/api/v1/admin/teachers/" + teacherId + "/resend-invite")
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-VAL-001"));
    }

    @Test
    void resendInviteForUnknownForeignOrNonTeacherReturns404() throws Exception {
        String admin = tokenFor("admin@alpha.test");

        mockMvc.perform(
                        post("/api/v1/admin/teachers/" + UUID.randomUUID() + "/resend-invite")
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-USR-002"));
        // A teacher of another centre is invisible here.
        UserAccount foreign =
                AuthFixtures.createStaff(
                        userAccountRepository, passwordEncoder, centreB, UserRole.TEACHER,
                        "foreign@beta.test", "Foreign Teacher", null, UserStatus.ACTIVE);
        mockMvc.perform(
                        post("/api/v1/admin/teachers/" + foreign.getUserId() + "/resend-invite")
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-USR-002"));
        // The admin's own account is not a teacher.
        mockMvc.perform(
                        post("/api/v1/admin/teachers/" + adminA.getUserId() + "/resend-invite")
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void resendInviteRequiresCentreAdmin() throws Exception {
        String admin = tokenFor("admin@alpha.test");
        UUID teacherId = inviteTeacherViaApi(admin, "guard@alpha.test");
        String teacher = tokenFor("tess@alpha.test");

        mockMvc.perform(
                        post("/api/v1/admin/teachers/" + teacherId + "/resend-invite")
                                .header("Authorization", "Bearer " + teacher))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivatedTeacherIsListedAsInactive() throws Exception {
        String admin = tokenFor("admin@alpha.test");
        mockMvc.perform(
                        delete("/api/v1/admin/teachers/" + teacherA2.getUserId())
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        String body =
                mockMvc.perform(
                                get("/api/v1/admin/teachers?page=0&size=20")
                                        .header("Authorization", "Bearer " + admin))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode content = objectMapper.readTree(body).get("content");
        boolean foundInactiveTim = false;
        for (JsonNode teacher : content) {
            if (teacher.get("userId").asString().equals(teacherA2.getUserId().toString())) {
                foundInactiveTim = !teacher.get("active").asBoolean();
            }
        }
        assertThat(foundInactiveTim).isTrue();
    }

    // ---------- batches ----------

    @Test
    void createBatchReturnsDtoWithGradeLabel() throws Exception {
        String admin = tokenFor("admin@alpha.test");

        JsonNode batch = createBatch(admin, "Batch B");
        assertThat(batch.get("name").asString()).isEqualTo("Batch B");
        assertThat(batch.get("gradeLabel").asString()).isEqualTo("Grade 8 · Mathematics");
        assertThat(batch.get("teacherUserId").isNull()).isTrue();
        assertThat(batch.get("studentCount").asLong()).isEqualTo(0L);
    }

    @Test
    void createBatchWithUnknownGradeSubjectReturns400() throws Exception {
        String admin = tokenFor("admin@alpha.test");

        mockMvc.perform(
                        post("/api/v1/admin/batches")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Batch X\",\"gradeSubjectId\":\""
                                                + UUID.randomUUID()
                                                + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-VAL-001"));
    }

    @Test
    void assignTeacherMakesTheBatchVisibleToThatTeacherOnly() throws Exception {
        String admin = tokenFor("admin@alpha.test");
        JsonNode batch = createBatch(admin, "Batch B");
        String batchId = batch.get("batchId").asString();

        mockMvc.perform(
                        put("/api/v1/admin/batches/" + batchId + "/teacher")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"" + teacherA1.getUserId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherUserId").value(teacherA1.getUserId().toString()))
                .andExpect(jsonPath("$.teacherName").value("Tess Teacher"));

        String tess = tokenFor("tess@alpha.test");
        mockMvc.perform(get("/api/v1/batches").header("Authorization", "Bearer " + tess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].batchId").value(batchId));

        String tim = tokenFor("tim@alpha.test");
        mockMvc.perform(get("/api/v1/batches").header("Authorization", "Bearer " + tim))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/v1/batches").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void assignTeacherRejectsUnknownBatchForeignBatchAndNonTeacher() throws Exception {
        String admin = tokenFor("admin@alpha.test");
        JsonNode batch = createBatch(admin, "Batch B");
        String batchId = batch.get("batchId").asString();
        UserAccount student =
                AuthFixtures.createStaff(
                        userAccountRepository, passwordEncoder, centreA, UserRole.STUDENT,
                        null, "Stu Student", null, UserStatus.ACTIVE);

        // The admin account is not a TEACHER.
        mockMvc.perform(
                        put("/api/v1/admin/batches/" + batchId + "/teacher")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"" + adminA.getUserId() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-USR-002"));
        // A student cannot be assigned as teacher.
        mockMvc.perform(
                        put("/api/v1/admin/batches/" + batchId + "/teacher")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"" + student.getUserId() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-USR-002"));
        // Unknown batch.
        mockMvc.perform(
                        put("/api/v1/admin/batches/" + UUID.randomUUID() + "/teacher")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"" + teacherA1.getUserId() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-CTR-001"));
    }

    @Test
    void assignTeacherConflictsWithAStudentMembership() throws Exception {
        String admin = tokenFor("admin@alpha.test");
        JsonNode batch = createBatch(admin, "Batch B");
        UUID batchId = UUID.fromString(batch.get("batchId").asString());
        Batch batchEntity = batchRepository.findById(batchId).orElseThrow();
        BatchMembership membership = new BatchMembership();
        membership.setCentreId(centreA.getCentreId());
        membership.setBatch(batchEntity);
        membership.setUser(teacherA1);
        membership.setMemberRole(MemberRole.STUDENT);
        batchMembershipRepository.save(membership);

        mockMvc.perform(
                        put("/api/v1/admin/batches/" + batchId + "/teacher")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"" + teacherA1.getUserId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-VAL-001"));
    }

    @Test
    void unassignTeacherRemovesVisibilityAndSecondCallIs404() throws Exception {
        String admin = tokenFor("admin@alpha.test");
        JsonNode batch = createBatch(admin, "Batch B");
        String batchId = batch.get("batchId").asString();
        mockMvc.perform(
                        put("/api/v1/admin/batches/" + batchId + "/teacher")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"" + teacherA1.getUserId() + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(
                        delete("/api/v1/admin/batches/" + batchId + "/teacher/" + teacherA1.getUserId())
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mockMvc.perform(
                        delete("/api/v1/admin/batches/" + batchId + "/teacher/" + teacherA1.getUserId())
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-CTR-002"));
    }

    @Test
    void reassignAfterUnassignReactivatesTheSameMembership() throws Exception {
        String admin = tokenFor("admin@alpha.test");
        JsonNode batch = createBatch(admin, "Batch B");
        String batchId = batch.get("batchId").asString();
        String assignBody = "{\"userId\":\"" + teacherA1.getUserId() + "\"}";

        mockMvc.perform(
                        put("/api/v1/admin/batches/" + batchId + "/teacher")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(assignBody))
                .andExpect(status().isOk());
        mockMvc.perform(
                        delete("/api/v1/admin/batches/" + batchId + "/teacher/" + teacherA1.getUserId())
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        // Re-assigning reactivates the INACTIVE membership instead of duplicating it.
        mockMvc.perform(
                        put("/api/v1/admin/batches/" + batchId + "/teacher")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(assignBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teacherUserId").value(teacherA1.getUserId().toString()));
        String tess = tokenFor("tess@alpha.test");
        mockMvc.perform(get("/api/v1/batches").header("Authorization", "Bearer " + tess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void assignDeactivatedTeacherReturns404() throws Exception {
        String admin = tokenFor("admin@alpha.test");
        JsonNode batch = createBatch(admin, "Batch B");
        String batchId = batch.get("batchId").asString();
        mockMvc.perform(
                        delete("/api/v1/admin/teachers/" + teacherA2.getUserId())
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        put("/api/v1/admin/batches/" + batchId + "/teacher")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"" + teacherA2.getUserId() + "\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-USR-002"));
    }

    @Test
    void unassignTeacherForAStudentMembershipReturns404() throws Exception {
        String admin = tokenFor("admin@alpha.test");
        JsonNode batch = createBatch(admin, "Batch B");
        String batchId = batch.get("batchId").asString();
        String addedBody =
                mockMvc.perform(
                                post("/api/v1/batches/" + batchId + "/students")
                                        .header("Authorization", "Bearer " + admin)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"fullName\":\"Stu One\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String studentUserId = objectMapper.readTree(addedBody).get("userId").asString();

        // The student holds a STUDENT membership, not a teacher assignment.
        mockMvc.perform(
                        delete("/api/v1/admin/batches/" + batchId + "/teacher/" + studentUserId)
                                .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-CTR-002"));
    }

    // ---------- centre profile + catalogues ----------

    @Test
    void centreProfileReadAndUpdate() throws Exception {
        String admin = tokenFor("admin@alpha.test");
        String tess = tokenFor("tess@alpha.test");

        mockMvc.perform(get("/api/v1/centre").header("Authorization", "Bearer " + tess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha Centre"))
                .andExpect(jsonPath("$.centreId").value(centreA.getCentreId().toString()));

        mockMvc.perform(
                        put("/api/v1/centre")
                                .header("Authorization", "Bearer " + tess)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        put("/api/v1/centre")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"Alpha Learning\",\"logoUrl\":\"https://example.com/logo.png\",\"contactEmail\":\"hi@alpha.test\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha Learning"))
                .andExpect(jsonPath("$.logoUrl").value("https://example.com/logo.png"))
                .andExpect(jsonPath("$.contactEmail").value("hi@alpha.test"));

        mockMvc.perform(
                        put("/api/v1/centre")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Alpha\",\"logoUrl\":\"not a url\",\"contactEmail\":\"bad\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.logoUrl").exists())
                .andExpect(jsonPath("$.fieldErrors.contactEmail").exists());
    }

    @Test
    void centreUpdateAcceptsNullBrandingFields() throws Exception {
        String admin = tokenFor("admin@alpha.test");

        mockMvc.perform(
                        put("/api/v1/centre")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Alpha Learning\",\"logoUrl\":null,\"contactEmail\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alpha Learning"))
                .andExpect(jsonPath("$.logoUrl").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void gradeSubjectsCatalogueListsPairings() throws Exception {
        String tess = tokenFor("tess@alpha.test");

        String body =
                mockMvc.perform(
                                get("/api/v1/master/grade-subjects")
                                        .header("Authorization", "Bearer " + tess))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode pairings = objectMapper.readTree(body);
        boolean found = false;
        for (JsonNode pairing : pairings) {
            if (pairing.get("gradeSubjectId").asString().equals(gradeSubject.getGradeSubjectId().toString())) {
                found = true;
                assertThat(pairing.get("label").asString()).isEqualTo("Grade 8 · Mathematics");
                assertThat(pairing.get("gradeCode").asString()).isEqualTo("G8");
                assertThat(pairing.get("subjectName").asString()).isEqualTo("Mathematics");
            }
        }
        assertThat(found).isTrue();
    }

    @Test
    void publicPickersExposeNamesOnly() throws Exception {
        String admin = tokenFor("admin@alpha.test");
        createBatch(admin, "Zulu Batch");
        createBatch(admin, "Alpha Batch");
        Batch inactive = new Batch();
        inactive.setCentre(centreA);
        inactive.setGradeSubject(gradeSubject);
        inactive.setName("Retired Batch");
        inactive.setActive(false);
        batchRepository.save(inactive);

        mockMvc.perform(get("/api/v1/public/centres"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Alpha Centre')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Beta Centre')]").exists());

        mockMvc.perform(get("/api/v1/public/centres/" + centreA.getCentreId() + "/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Alpha Batch"))
                .andExpect(jsonPath("$[1].name").value("Zulu Batch"));

        mockMvc.perform(get("/api/v1/public/centres/" + UUID.randomUUID() + "/batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
