package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.learnium.material.PdfFixtures;
import com.learnium.model.common.UserStatus;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.master.Subject;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.content.MaterialRepository;
import com.learnium.repository.master.GradeRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.master.SubjectRepository;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.TokenHashing;
import com.learnium.storage.StorageService;
import com.learnium.testsupport.AuthFixtures;
import com.learnium.testsupport.CommittedDataCleanup;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Materials over HTTP (M3 branch 1): upload/paste, extraction outcomes,
 * same-uploader dedupe, creator/admin visibility, soft delete. Committed
 * state, like the other flow suites.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MaterialFlowTest {

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
    @Autowired private MaterialRepository materialRepository;
    @Autowired private StorageService storageService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    private UserAccount teacherA1;
    private GradeSubject gradeSubject;
    private String adminToken;
    private String teacherToken;
    private String teacher2Token;
    private String otherCentreTeacherToken;

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
        teacherA1 =
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

        adminToken = login("admin@alpha.test");
        teacherToken = login("tess@alpha.test");
        teacher2Token = login("tim@alpha.test");
        otherCentreTeacherToken = login("bea@beta.test");
    }

    @Test
    void uploadExtractsHashesAndStoresTheFile() throws Exception {
        byte[] pdf = PdfFixtures.pdfOf(List.of(linesOf(PROSE.repeat(3))));

        JsonNode created =
                upload(teacherToken, pdf, "chapter.pdf", "Chapter 4: Fractions")
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.extractionStatus").value("OK"))
                        .andExpect(jsonPath("$.sourceType").value("PDF"))
                        .andExpect(jsonPath("$.deduplicated").value(false))
                        .andExpect(jsonPath("$.gradeCode").value("G8"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
                        .transform(this::readTree);

        UUID materialId = UUID.fromString(created.get("materialId").asString());
        JsonNode detail =
                readTree(
                        mockMvc.perform(
                                        get("/api/v1/materials/" + materialId)
                                                .header("Authorization", "Bearer " + teacherToken))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        String extractedText = detail.get("extractedText").asString();
        assertThat(extractedText.replaceAll("\\s+", " "))
                .contains("Fractions represent parts of a whole");
        // The recorded hash is the SHA-256 of exactly the stored text.
        assertThat(created.get("contentHash").asString())
                .isEqualTo(TokenHashing.sha256Hex(extractedText));

        var material = materialRepository.findById(materialId).orElseThrow();
        assertThat(material.getStorageKey()).isNotNull();
        assertThat(storageService.read(material.getStorageKey())).isEqualTo(pdf);
    }

    @Test
    void uploadOfScannedPdfRecordsNoTextAndNoHash() throws Exception {
        byte[] pdf = PdfFixtures.pdfOf(List.of(List.of(), List.of()));

        JsonNode created =
                readTree(
                        upload(teacherToken, pdf, "scan.pdf", "Scanned chapter")
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.extractionStatus").value("NO_TEXT"))
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        // Two different scans must never dedupe into each other: no hash.
        JsonNode hash = created.get("contentHash");
        assertThat(hash == null || hash.isNull()).isTrue();
    }

    @Test
    void uploadRejectsNonPdfExtensionAndMissingFile() throws Exception {
        byte[] pdf = PdfFixtures.pdfOf(List.of(linesOf(PROSE)));

        upload(teacherToken, pdf, "chapter.txt", "Wrong extension")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-MAT-001"));

        mockMvc.perform(
                        multipart("/api/v1/materials")
                                .param("title", "No file")
                                .param("gradeSubjectId", gradeSubject.getGradeSubjectId().toString())
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-MAT-001"));
    }

    @Test
    void uploadValidatesTitleAndGradeSubject() throws Exception {
        byte[] pdf = PdfFixtures.pdfOf(List.of(linesOf(PROSE)));

        upload(teacherToken, pdf, "chapter.pdf", "x".repeat(201))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-VAL-001"));

        mockMvc.perform(
                        multipart("/api/v1/materials")
                                .file(new MockMultipartFile("file", "chapter.pdf", "application/pdf", pdf))
                                .param("title", "Unknown pairing")
                                .param("gradeSubjectId", UUID.randomUUID().toString())
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-SYS-404"));
    }

    @Test
    void uploadValidatesMissingPieces() throws Exception {
        byte[] pdf = PdfFixtures.pdfOf(List.of(linesOf(PROSE)));

        // No gradeSubjectId param at all.
        mockMvc.perform(
                        multipart("/api/v1/materials")
                                .file(new MockMultipartFile("file", "chapter.pdf", "application/pdf", pdf))
                                .param("title", "No pairing")
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-VAL-001"));

        // No title param, then a blank title.
        mockMvc.perform(
                        multipart("/api/v1/materials")
                                .file(new MockMultipartFile("file", "chapter.pdf", "application/pdf", pdf))
                                .param("gradeSubjectId", gradeSubject.getGradeSubjectId().toString())
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-VAL-001"));
        upload(teacherToken, pdf, "chapter.pdf", "   ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-VAL-001"));

        // An empty file part, and a part with no filename at all.
        mockMvc.perform(
                        multipart("/api/v1/materials")
                                .file(new MockMultipartFile("file", "chapter.pdf", "application/pdf", new byte[0]))
                                .param("title", "Empty")
                                .param("gradeSubjectId", gradeSubject.getGradeSubjectId().toString())
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-MAT-001"));
        mockMvc.perform(
                        multipart("/api/v1/materials")
                                .file(new MockMultipartFile("file", null, "application/pdf", pdf))
                                .param("title", "Nameless")
                                .param("gradeSubjectId", gradeSubject.getGradeSubjectId().toString())
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-MAT-001"));
    }

    @Test
    void pasteSameTextTwiceDedupes() throws Exception {
        JsonNode first = paste(teacherToken, "Pasted once", PROSE.repeat(3));

        mockMvc.perform(
                        post("/api/v1/materials/text")
                                .header("Authorization", "Bearer " + teacherToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(textRequest("Pasted twice", PROSE.repeat(3))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deduplicated").value(true))
                .andExpect(jsonPath("$.materialId").value(first.get("materialId").asString()));
    }

    @Test
    void legacyMaterialWithoutExtractionStatusRenders() throws Exception {
        // A row predating V10 has no extraction outcome yet; the DTOs must
        // render it with a null status instead of failing.
        var legacy = new com.learnium.model.content.Material();
        legacy.setCentre(teacherA1.getCentre());
        legacy.setUploadedBy(teacherA1);
        legacy.setGradeSubject(gradeSubject);
        legacy.setTitle("Legacy scanned chapter");
        legacy.setSourceType(com.learnium.model.content.MaterialSourceType.PDF);
        legacy = materialRepository.save(legacy);

        mockMvc.perform(
                        get("/api/v1/materials/" + legacy.getMaterialId())
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Legacy scanned chapter"));
        mockMvc.perform(get("/api/v1/materials").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void uploadBeyondTheServletCapIsMatTooLarge() throws Exception {
        byte[] pdf = PdfFixtures.pdfOf(List.of(linesOf(PROSE)));
        byte[] oversized = new byte[21 * 1024 * 1024];
        System.arraycopy(pdf, 0, oversized, 0, pdf.length);

        upload(teacherToken, oversized, "huge.pdf", "Huge chapter")
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("LRN-MAT-003"));
    }

    @Test
    void pasteCreatesMaterialWithTheSameHashing() throws Exception {
        String text = PROSE.repeat(3);

        mockMvc.perform(
                        post("/api/v1/materials/text")
                                .header("Authorization", "Bearer " + teacherToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(textRequest("Pasted chapter", text)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.extractionStatus").value("OK"))
                .andExpect(jsonPath("$.sourceType").value("PASTE"))
                .andExpect(jsonPath("$.contentHash").value(TokenHashing.sha256Hex(text)));
    }

    @Test
    void pasteValidatesTheBody() throws Exception {
        mockMvc.perform(
                        post("/api/v1/materials/text")
                                .header("Authorization", "Bearer " + teacherToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(textRequest("Blank text", "   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("LRN-VAL-001"));
    }

    @Test
    void sameUploaderReuploadDedupesButAnotherTeacherDoesNot() throws Exception {
        byte[] pdf = PdfFixtures.pdfOf(List.of(linesOf(PROSE.repeat(3))));

        JsonNode first =
                readTree(
                        upload(teacherToken, pdf, "chapter.pdf", "Chapter 4")
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());

        upload(teacherToken, pdf, "chapter-again.pdf", "Chapter 4 (again)")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deduplicated").value(true))
                .andExpect(jsonPath("$.materialId").value(first.get("materialId").asString()));
        assertThat(materialRepository.findByCentreId(teacherA1.getCentre().getCentreId()))
                .hasSize(1);

        upload(teacher2Token, pdf, "chapter.pdf", "Tim's copy")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deduplicated").value(false));
    }

    @Test
    void listScopesTeachersToTheirOwnAndAdminsToTheCentre() throws Exception {
        paste(teacherToken, "Tess one", PROSE.repeat(3));
        paste(teacherToken, "Tess two", PROSE.repeat(4));
        paste(teacher2Token, "Tim one", PROSE.repeat(5));

        mockMvc.perform(get("/api/v1/materials").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/materials").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3));
        mockMvc.perform(
                        get("/api/v1/materials")
                                .header("Authorization", "Bearer " + otherCentreTeacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void detailIsHiddenFromOtherTeachersAndOtherCentres() throws Exception {
        JsonNode created = paste(teacherToken, "Private chapter", PROSE.repeat(3));
        String id = created.get("materialId").asString();

        mockMvc.perform(
                        get("/api/v1/materials/" + id)
                                .header("Authorization", "Bearer " + teacher2Token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-MAT-002"));
        mockMvc.perform(
                        get("/api/v1/materials/" + id)
                                .header("Authorization", "Bearer " + otherCentreTeacherToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LRN-MAT-002"));
        mockMvc.perform(
                        get("/api/v1/materials/" + id).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void deleteIsSoftAndScoped() throws Exception {
        JsonNode created = paste(teacherToken, "To delete", PROSE.repeat(3));
        String id = created.get("materialId").asString();

        // Another teacher cannot delete it (and cannot learn it exists).
        mockMvc.perform(
                        delete("/api/v1/materials/" + id)
                                .header("Authorization", "Bearer " + teacher2Token))
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        delete("/api/v1/materials/" + id)
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(
                        get("/api/v1/materials/" + id)
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/materials").header("Authorization", "Bearer " + teacherToken))
                .andExpect(jsonPath("$.totalElements").value(0));
        // Second delete is a 404, and the row itself survives, marked.
        mockMvc.perform(
                        delete("/api/v1/materials/" + id)
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNotFound());
        var row = materialRepository.findById(UUID.fromString(id)).orElseThrow();
        assertThat(row.getDeletedAt()).isNotNull();
        assertThat(materialRepository.findCleanupCandidates()).extracting("materialId")
                .containsExactly(row.getMaterialId());
    }

    @Test
    void adminMayDeleteATeachersMaterial() throws Exception {
        JsonNode created = paste(teacherToken, "Admin cleanup", PROSE.repeat(3));
        String id = created.get("materialId").asString();

        mockMvc.perform(
                        delete("/api/v1/materials/" + id)
                                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    void studentsAndAnonymousCallersAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/materials"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-001"));

        String studentToken = studentToken();
        mockMvc.perform(
                        get("/api/v1/materials").header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LRN-AUTH-003"));
    }

    // --- helpers -------------------------------------------------------

    private String studentToken() throws Exception {
        JsonNode batch =
                readTree(
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
                                .getContentAsString());
        String batchId = batch.get("batchId").asString();
        JsonNode student =
                readTree(
                        mockMvc.perform(
                                        post("/api/v1/batches/" + batchId + "/students")
                                                .header("Authorization", "Bearer " + adminToken)
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content("{\"fullName\":\"Simran Kaur\"}"))
                                .andExpect(status().isCreated())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        String body =
                mockMvc.perform(
                                post("/api/v1/auth/student-session")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"batchId\":\""
                                                        + batchId
                                                        + "\",\"fullName\":\"Simran Kaur\",\"pin\":\""
                                                        + student.get("pin").asString()
                                                        + "\"}"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return readTree(body).get("accessToken").asString();
    }

    private ResultActions upload(String token, byte[] pdf, String filename, String title)
            throws Exception {
        return mockMvc.perform(
                multipart("/api/v1/materials")
                        .file(new MockMultipartFile("file", filename, "application/pdf", pdf))
                        .param("title", title)
                        .param("gradeSubjectId", gradeSubject.getGradeSubjectId().toString())
                        .header("Authorization", "Bearer " + token));
    }

    private JsonNode paste(String token, String title, String text) throws Exception {
        return readTree(
                mockMvc.perform(
                                post("/api/v1/materials/text")
                                        .header("Authorization", "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(textRequest(title, text)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString());
    }

    private String textRequest(String title, String text) throws Exception {
        return objectMapper.writeValueAsString(
                java.util.Map.of(
                        "title", title,
                        "gradeSubjectId", gradeSubject.getGradeSubjectId().toString(),
                        "text", text));
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

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> linesOf(String text) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < text.length(); i += 78) {
            lines.add(text.substring(i, Math.min(text.length(), i + 78)));
        }
        return lines;
    }
}
