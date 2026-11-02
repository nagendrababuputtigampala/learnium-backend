package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learnium.generation.GeneratedQuestionType;
import com.learnium.generation.GenerationResultCounts;
import com.learnium.model.common.UserStatus;
import com.learnium.model.content.BackgroundJob;
import com.learnium.model.content.BackgroundJobStatus;
import com.learnium.model.content.GenerationDifficulty;
import com.learnium.model.content.GenerationFailureKind;
import com.learnium.model.content.Material;
import com.learnium.model.content.Question;
import com.learnium.model.content.QuestionStatus;
import com.learnium.model.content.Topic;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.master.QuestionType;
import com.learnium.model.master.Subject;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.content.BackgroundJobRepository;
import com.learnium.repository.content.MaterialRepository;
import com.learnium.repository.content.QuestionRepository;
import com.learnium.repository.content.TopicRepository;
import com.learnium.repository.master.QuestionTypeRepository;
import com.learnium.repository.master.GradeRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.master.SubjectRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.testsupport.AuthFixtures;
import com.learnium.testsupport.CommittedDataCleanup;
import java.time.Instant;
import java.util.EnumMap;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Generation jobs over HTTP (M3 branch 2): the creation validation
 * matrix (LRN-GEN-001..004), frozen job inputs, and the read paths'
 * creator/admin visibility. Committed state, like the other flow suites.
 * Execution is covered by the worker suites — the scheduler is disabled
 * in the test profile, so jobs stay PENDING here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GenerationFlowTest {

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
    @Autowired private BackgroundJobRepository jobRepository;
    @Autowired private MaterialRepository materialRepository;
    @Autowired private QuestionRepository questionRepository;
    @Autowired private QuestionTypeRepository questionTypeRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UserAccount teacherA;
    private GradeSubject gradeSubject;
    private Topic fractionsTopic;
    private Topic otherTopic;
    private String adminToken;
    private String teacherToken;
    private String teacher2Token;
    private String otherCentreTeacherToken;
    private UUID materialId;
    private String materialHash;

    @AfterEach
    void cleanUp() {
        CommittedDataCleanup.clearAll(jdbcTemplate);
    }

    @BeforeEach
    void setUp() throws Exception {
        Centre centreA = AuthFixtures.createCentre(centreRepository, "Alpha Centre");
        Centre centreB = AuthFixtures.createCentre(centreRepository, "Beta Centre");
        AuthFixtures.createStaff(
                userAccountRepository, passwordEncoder, centreA, UserRole.CENTRE_ADMIN,
                "admin@alpha.test", "Ann Admin", PASSWORD, UserStatus.ACTIVE);
        teacherA =
                AuthFixtures.createStaff(
                        userAccountRepository, passwordEncoder, centreA, UserRole.TEACHER,
                        "tess@alpha.test", "Tess Teacher", PASSWORD, UserStatus.ACTIVE);
        AuthFixtures.createStaff(
                userAccountRepository, passwordEncoder, centreA, UserRole.TEACHER,
                "tim@alpha.test", "Tim Teacher", PASSWORD, UserStatus.ACTIVE);
        AuthFixtures.createStaff(
                userAccountRepository, passwordEncoder, centreB, UserRole.TEACHER,
                "bea@beta.test", "Bea Beta", PASSWORD, UserStatus.ACTIVE);

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
        GradeSubject otherGs = new GradeSubject();
        otherGs.setGrade(grade);
        Subject science = new Subject();
        science.setSubjectName("Science");
        otherGs.setSubject(subjectRepository.save(science));
        otherGs = gradeSubjectRepository.save(otherGs);

        fractionsTopic = saveTopic(gradeSubject, "Fractions");
        otherTopic = saveTopic(otherGs, "Cells");

        adminToken = login("admin@alpha.test");
        teacherToken = login("tess@alpha.test");
        teacher2Token = login("tim@alpha.test");
        otherCentreTeacherToken = login("bea@beta.test");

        JsonNode material = pasteMaterial(teacherToken, "Chapter 4: Fractions", PROSE.repeat(3));
        materialId = UUID.fromString(material.get("materialId").asString());
        materialHash = material.get("contentHash").asString();
    }

    @Test
    void createFreezesTheJobAndAnswers202() throws Exception {
        JsonNode job =
                readTree(
                        generate(teacherToken, materialId, validBody())
                                .andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.status").value("PENDING"))
                                .andExpect(jsonPath("$.requestedCounts.MCQ").value(3))
                                .andExpect(jsonPath("$.requestedCounts.WRITTEN").value(2))
                                .andExpect(jsonPath("$.requestedCounts.NUMERIC").value(0))
                                .andExpect(jsonPath("$.difficulty").value("HARD"))
                                .andExpect(jsonPath("$.provider").value("gemini"))
                                .andExpect(jsonPath("$.model").value("gemini-2.0-flash"))
                                .andExpect(jsonPath("$.attempts").value(0))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());

        var saved = jobRepository.findById(UUID.fromString(job.get("id").asString())).orElseThrow();
        assertThat(saved.getContentHash()).isEqualTo(materialHash);
        assertThat(saved.getPromptVersion()).isEqualTo("gen-prompt-v1");
        assertThat(saved.getPayloadSchemaVersion()).isEqualTo("gen-payload-v1");
        assertThat(saved.getCreatedBy()).isEqualTo(teacherA.getUserId());
        assertThat(saved.getTopic().getTopicId()).isEqualTo(fractionsTopic.getTopicId());
        assertThat(saved.getNextAttemptAt()).isNotNull();
    }

    @Test
    void createDefaultsDifficultyToMedium() throws Exception {
        String body =
                "{\"topicId\":\"" + fractionsTopic.getTopicId() + "\",\"counts\":{\"MCQ\":1}}";
        generate(teacherToken, materialId, body)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.difficulty").value("MEDIUM"));
    }

    @Test
    void createRejectsInvalidDifficulty() throws Exception {
        String body =
                "{\"topicId\":\""
                        + fractionsTopic.getTopicId()
                        + "\",\"counts\":{\"MCQ\":1},\"difficulty\":\"EXTREME\"}";
        generate(teacherToken, materialId, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-VAL-001"));
    }

    @Test
    void createRejectsMaterialWhoseTextIsUnusable() throws Exception {
        JsonNode weak = pasteMaterial(teacherToken, "Tiny chapter", "Short text.");
        generate(
                        teacherToken,
                        UUID.fromString(weak.get("materialId").asString()),
                        validBody())
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("LRN-GEN-001"));
    }

    @Test
    void createHidesMaterialsFromOtherTeachersAndCentres() throws Exception {
        generate(teacher2Token, materialId, validBody())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-MAT-002"));
        generate(otherCentreTeacherToken, materialId, validBody())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-MAT-002"));
        generate(teacherToken, UUID.randomUUID(), validBody())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-MAT-002"));
        // The centre admin may generate from any material in the centre.
        generate(adminToken, materialId, validBody()).andExpect(status().isAccepted());
    }

    @Test
    void createRejectsSoftDeletedMaterial() throws Exception {
        mockMvc.perform(
                        delete("/api/v1/materials/" + materialId)
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNoContent());
        generate(teacherToken, materialId, validBody())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-MAT-002"));
    }

    @Test
    void createRejectsTopicsOutsideTheMaterialsGradeSubject() throws Exception {
        String mismatch =
                "{\"topicId\":\"" + otherTopic.getTopicId() + "\",\"counts\":{\"MCQ\":1}}";
        generate(teacherToken, materialId, mismatch)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-GEN-002"));
        String unknown = "{\"topicId\":\"" + UUID.randomUUID() + "\",\"counts\":{\"MCQ\":1}}";
        generate(teacherToken, materialId, unknown)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-GEN-002"));
        generate(teacherToken, materialId, "{\"counts\":{\"MCQ\":1}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-GEN-002"));
    }

    @Test
    void createValidatesCounts() throws Exception {
        String topic = "\"topicId\":\"" + fractionsTopic.getTopicId() + "\"";
        generate(teacherToken, materialId, "{" + topic + ",\"counts\":{}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-GEN-003"));
        generate(teacherToken, materialId, "{" + topic + ",\"counts\":{\"MCQ\":41}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-GEN-003"));
        generate(
                        teacherToken,
                        materialId,
                        "{" + topic + ",\"counts\":{\"MCQ\":30,\"WRITTEN\":11}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-GEN-003"));
        generate(teacherToken, materialId, "{" + topic + ",\"counts\":{\"MCQ\":-1,\"WRITTEN\":2}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-GEN-003"));
        generate(teacherToken, materialId, "{" + topic + ",\"counts\":{\"ESSAY\":2}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-GEN-003"));
        generate(teacherToken, materialId, "{" + topic + ",\"counts\":{\"MCQ\":null}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-GEN-003"));
        // The boundary: exactly 40 in one type is valid.
        generate(teacherToken, materialId, "{" + topic + ",\"counts\":{\"FLASHCARD\":40}}")
                .andExpect(status().isAccepted());
    }

    @Test
    void getJobIsVisibleToCreatorAndAdminOnly() throws Exception {
        JsonNode job =
                readTree(
                        generate(adminToken, materialId, validBody())
                                .andExpect(status().isAccepted())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        String jobId = job.get("id").asString();

        mockMvc.perform(
                        get("/api/v1/generation-jobs/" + jobId)
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.contentHash").value(materialHash))
                .andExpect(jsonPath("$.promptVersion").value("gen-prompt-v1"))
                .andExpect(jsonPath("$.resultCounts").isEmpty())
                .andExpect(jsonPath("$.failureKind").isEmpty())
                .andExpect(jsonPath("$.finishedAt").isEmpty());
        // Another teacher in the centre cannot see the admin's job.
        mockMvc.perform(
                        get("/api/v1/generation-jobs/" + jobId)
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-GEN-005"));
        mockMvc.perform(
                        get("/api/v1/generation-jobs/" + jobId)
                                .header("Authorization", "Bearer " + otherCentreTeacherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-GEN-005"));
        mockMvc.perform(
                        get("/api/v1/generation-jobs/" + UUID.randomUUID())
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-GEN-005"));
    }

    @Test
    void listQuestionsStartsEmptyAndFollowsMaterialVisibility() throws Exception {
        mockMvc.perform(
                        get("/api/v1/materials/" + materialId + "/questions")
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
        mockMvc.perform(
                        get("/api/v1/materials/" + materialId + "/questions")
                                .header("Authorization", "Bearer " + teacher2Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-MAT-002"));
    }

    @Test
    void getJobIsVisibleToItsTeacherCreator() throws Exception {
        JsonNode job =
                readTree(
                        generate(teacherToken, materialId, validBody())
                                .andExpect(status().isAccepted())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());

        mockMvc.perform(
                        get("/api/v1/generation-jobs/" + job.get("id").asString())
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createWithoutCountsIsRejected() throws Exception {
        String body = "{\"topicId\":\"" + fractionsTopic.getTopicId() + "\"}";

        generate(teacherToken, materialId, body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-GEN-003"));
    }

    @Test
    void createWithBlankDifficultyDefaultsToMedium() throws Exception {
        String body =
                "{\"topicId\":\""
                        + fractionsTopic.getTopicId()
                        + "\",\"counts\":{\"MCQ\":1},\"difficulty\":\"\"}";

        generate(teacherToken, materialId, body)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.difficulty").value("MEDIUM"));
    }

    @Test
    void getJobShowsCountsUsageAndFailureOnceFinished() throws Exception {
        JsonNode created =
                readTree(
                        generate(teacherToken, materialId, validBody())
                                .andExpect(status().isAccepted())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        BackgroundJob job =
                jobRepository
                        .findById(UUID.fromString(created.get("id").asString()))
                        .orElseThrow();
        EnumMap<GeneratedQuestionType, GenerationResultCounts.TypeResult> byType =
                new EnumMap<>(GeneratedQuestionType.class);
        for (GeneratedQuestionType type : GeneratedQuestionType.values()) {
            byType.put(type, new GenerationResultCounts.TypeResult(0, 0, 0, 0, Map.of()));
        }
        byType.put(
                GeneratedQuestionType.MCQ,
                new GenerationResultCounts.TypeResult(3, 3, 0, 3, Map.of("SCHEMA_INVALID", 3)));
        job.setStatus(BackgroundJobStatus.FAILED);
        job.setFailureKind(GenerationFailureKind.ZERO_VALID);
        job.setResultCounts(new GenerationResultCounts(byType).toJson());
        job.setUsageInputTokens(120L);
        job.setUsageOutputTokens(45L);
        job.setFinishedAt(Instant.parse("2026-10-29T01:00:00Z"));
        jobRepository.save(job);

        mockMvc.perform(
                        get("/api/v1/generation-jobs/" + job.getId())
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureKind").value("ZERO_VALID"))
                .andExpect(jsonPath("$.usageInputTokens").value(120))
                .andExpect(jsonPath("$.usageOutputTokens").value(45))
                .andExpect(jsonPath("$.usageThinkingTokens").isEmpty())
                .andExpect(jsonPath("$.finishedAt").isNotEmpty())
                .andExpect(jsonPath("$.resultCounts.MCQ.requested").value(3))
                .andExpect(jsonPath("$.resultCounts.MCQ.received").value(3))
                .andExpect(jsonPath("$.resultCounts.MCQ.accepted").value(0))
                .andExpect(jsonPath("$.resultCounts.MCQ.dropped").value(3))
                .andExpect(jsonPath("$.resultCounts.MCQ.dropReasons.SCHEMA_INVALID").value(3));
    }

    @Test
    void listQuestionsReturnsDraftsNewestFirstWithProvenance() throws Exception {
        JsonNode created =
                readTree(
                        generate(teacherToken, materialId, validBody())
                                .andExpect(status().isAccepted())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        UUID jobId = UUID.fromString(created.get("id").asString());
        Material material = materialRepository.findById(materialId).orElseThrow();
        QuestionType mcq =
                questionTypeRepository
                        .findByCode("MCQ")
                        .orElseGet(
                                () -> {
                                    QuestionType type = new QuestionType();
                                    type.setCode("MCQ");
                                    type.setName("MCQ");
                                    return questionTypeRepository.save(type);
                                });
        Question older = saveDraft(material, mcq, jobId, "Older draft?", "a");
        Thread.sleep(10);
        Question newer = saveDraft(material, mcq, jobId, "Newer draft?", "b");

        mockMvc.perform(
                        get("/api/v1/materials/" + materialId + "/questions")
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].questionId").value(newer.getQuestionId().toString()))
                .andExpect(jsonPath("$.content[0].type").value("MCQ"))
                .andExpect(jsonPath("$.content[0].payload.stem").value("Newer draft?"))
                .andExpect(jsonPath("$.content[0].correctRef").value("b"))
                .andExpect(jsonPath("$.content[0].generationJobId").value(jobId.toString()))
                .andExpect(jsonPath("$.content[0].provider").value("gemini"))
                .andExpect(jsonPath("$.content[0].model").value("fake-model"))
                .andExpect(jsonPath("$.content[0].promptVersion").value("gen-prompt-v1"))
                .andExpect(jsonPath("$.content[1].questionId").value(older.getQuestionId().toString()));
    }

    @Test
    void generationEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(
                        post("/api/v1/materials/" + materialId + "/generate")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validBody()))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ------------------------------------------------------------

    private Question saveDraft(
            Material material, QuestionType type, UUID jobId, String stem, String correctRef)
            throws Exception {
        Question question = new Question();
        question.setCentre(material.getCentre());
        question.setTopic(fractionsTopic);
        question.setQuestionType(type);
        question.setMaterial(material);
        question.setDifficulty((short) 3);
        question.setPayload(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree("{\"stem\":\"" + stem + "\",\"options\":[]}"));
        question.setCorrectRef(correctRef);
        question.setStatus(QuestionStatus.DRAFT);
        question.setGenerationJobId(jobId);
        question.setGenerationProvider("gemini");
        question.setGeneratedByModel("fake-model");
        question.setPromptVersion("gen-prompt-v1");
        question.setPayloadSchemaVersion("gen-payload-v1");
        question.setContentHash(materialHash);
        question.setCreatedBy(teacherA.getUserId());
        return questionRepository.save(question);
    }

    private Topic saveTopic(GradeSubject gradeSubject, String name) {
        Topic topic = new Topic();
        topic.setGradeSubject(gradeSubject);
        topic.setTopicName(name);
        return topicRepository.save(topic);
    }

    private String validBody() {
        return "{\"topicId\":\""
                + fractionsTopic.getTopicId()
                + "\",\"counts\":{\"MCQ\":3,\"WRITTEN\":2},\"difficulty\":\"HARD\"}";
    }

    private ResultActions generate(String token, UUID targetMaterialId, String body)
            throws Exception {
        return mockMvc.perform(
                post("/api/v1/materials/" + targetMaterialId + "/generate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private JsonNode pasteMaterial(String token, String title, String text) throws Exception {
        String body =
                objectMapper.writeValueAsString(
                        java.util.Map.of(
                                "title", title,
                                "gradeSubjectId", gradeSubject.getGradeSubjectId().toString(),
                                "text", text));
        return readTree(
                mockMvc.perform(
                                post("/api/v1/materials/text")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
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
        return readTree(body).get("accessToken").asString();
    }

    private JsonNode readTree(String json) throws Exception {
        return objectMapper.readTree(json);
    }
}
