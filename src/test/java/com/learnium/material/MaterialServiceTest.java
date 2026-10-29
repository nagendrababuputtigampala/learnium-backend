package com.learnium.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learnium.api.dto.MaterialDtos.CreateMaterialTextRequest;
import com.learnium.api.dto.MaterialDtos.MaterialDto;
import com.learnium.common.error.BusinessException;
import com.learnium.model.content.ExtractionStatus;
import com.learnium.model.content.Material;
import com.learnium.model.content.MaterialSourceType;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.master.Subject;
import com.learnium.model.user.UserRole;
import com.learnium.repository.content.MaterialRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.security.LearniumPrincipal;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service paths the HTTP layer cannot reach deterministically: an upload
 * stream that fails mid-read, and the creation-race recovery — the insert
 * losing to the dedupe unique index must come back as the winning material,
 * never as an error.
 */
class MaterialServiceTest {

    private final MaterialRepository materialRepository = mock(MaterialRepository.class);
    private final GradeSubjectRepository gradeSubjectRepository =
            mock(GradeSubjectRepository.class);
    private final ExtractionService extractionService = mock(ExtractionService.class);
    private final MaterialCreationService creationService = mock(MaterialCreationService.class);
    private final MaterialService service =
            new MaterialService(
                    materialRepository,
                    gradeSubjectRepository,
                    extractionService,
                    creationService);

    private final UUID centreId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final LearniumPrincipal principal =
            LearniumPrincipal.staff(userId, centreId, UserRole.TEACHER);
    private GradeSubject gradeSubject;

    @BeforeEach
    void setUp() {
        Grade grade = new Grade();
        grade.setGradeId(UUID.randomUUID());
        grade.setCode("G8");
        grade.setName("Grade 8");
        Subject subject = new Subject();
        subject.setSubjectId(UUID.randomUUID());
        subject.setSubjectName("Mathematics");
        gradeSubject = new GradeSubject();
        gradeSubject.setGradeSubjectId(UUID.randomUUID());
        gradeSubject.setGrade(grade);
        gradeSubject.setSubject(subject);
        when(gradeSubjectRepository.findById(gradeSubject.getGradeSubjectId()))
                .thenReturn(Optional.of(gradeSubject));
    }

    @Test
    void unreadableUploadStreamIsMatInvalidFile() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn("chapter.pdf");
        when(file.getBytes()).thenThrow(new IOException("stream reset"));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(
                        () ->
                                service.createFromUpload(
                                        principal,
                                        file,
                                        "Chapter",
                                        gradeSubject.getGradeSubjectId()))
                .satisfies(
                        e -> assertThat(e.getErrorCode().name()).isEqualTo("MAT_INVALID_FILE"));
    }

    @Test
    void uploadWithoutAFilenameIsMatInvalidFile() {
        // Real servlet stacks can deliver a part with no filename at all;
        // MockMvc normalizes that away, so it is pinned here with a mock.
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1024L);
        when(file.getOriginalFilename()).thenReturn(null);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(
                        () ->
                                service.createFromUpload(
                                        principal,
                                        file,
                                        "Chapter",
                                        gradeSubject.getGradeSubjectId()))
                .satisfies(
                        e -> assertThat(e.getErrorCode().name()).isEqualTo("MAT_INVALID_FILE"));
    }

    @Test
    void createTextReturnsTheCreationResult() {
        var request =
                new CreateMaterialTextRequest(
                        "Chapter", gradeSubject.getGradeSubjectId(), "real text");
        var extraction = new ExtractionResult(ExtractionStatus.OK, "real text", 9);
        var created = dto(UUID.randomUUID(), false);
        when(extractionService.extractText("real text")).thenReturn(extraction);
        when(creationService.insertText(
                        principal, "Chapter", gradeSubject.getGradeSubjectId(), extraction))
                .thenReturn(created);

        assertThat(service.createFromText(principal, request)).isSameAs(created);
    }

    @Test
    void lostTextRaceReturnsTheWinningMaterial() {
        var request =
                new CreateMaterialTextRequest(
                        "Chapter", gradeSubject.getGradeSubjectId(), "real text");
        var extraction = new ExtractionResult(ExtractionStatus.OK, "real text", 9);
        when(extractionService.extractText("real text")).thenReturn(extraction);
        when(creationService.insertText(any(), anyString(), any(), eq(extraction)))
                .thenThrow(new DataIntegrityViolationException("dedupe identity"));
        Material winner = winnerMaterial();
        when(materialRepository.findDedupeCandidate(centreId, userId, extraction.contentHash()))
                .thenReturn(Optional.of(winner));

        MaterialDto result = service.createFromText(principal, request);

        assertThat(result.materialId()).isEqualTo(winner.getMaterialId());
        assertThat(result.deduplicated()).isTrue();
    }

    @Test
    void lostUploadRaceReturnsTheWinningMaterial() throws Exception {
        byte[] pdfBytes = "%PDF-1.7 body".getBytes(StandardCharsets.UTF_8);
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) pdfBytes.length);
        when(file.getOriginalFilename()).thenReturn("chapter.pdf");
        when(file.getBytes()).thenReturn(pdfBytes);
        var extraction = new ExtractionResult(ExtractionStatus.OK, "real text", 9);
        when(extractionService.extractPdf(pdfBytes)).thenReturn(extraction);
        when(creationService.insertUpload(any(), anyString(), any(), anyString(), any(), eq(extraction)))
                .thenThrow(new DataIntegrityViolationException("dedupe identity"));
        Material winner = winnerMaterial();
        when(materialRepository.findDedupeCandidate(centreId, userId, extraction.contentHash()))
                .thenReturn(Optional.of(winner));

        MaterialDto result =
                service.createFromUpload(
                        principal, file, "Chapter", gradeSubject.getGradeSubjectId());

        assertThat(result.materialId()).isEqualTo(winner.getMaterialId());
        assertThat(result.deduplicated()).isTrue();
    }

    @Test
    void conflictWithoutAWinningMaterialRethrows() {
        var request =
                new CreateMaterialTextRequest(
                        "Chapter", gradeSubject.getGradeSubjectId(), "real text");
        var extraction = new ExtractionResult(ExtractionStatus.OK, "real text", 9);
        when(extractionService.extractText("real text")).thenReturn(extraction);
        var conflict = new DataIntegrityViolationException("some other constraint");
        when(creationService.insertText(any(), anyString(), any(), eq(extraction)))
                .thenThrow(conflict);
        when(materialRepository.findDedupeCandidate(any(), any(), anyString()))
                .thenReturn(Optional.empty());

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> service.createFromText(principal, request))
                .isSameAs(conflict);
    }

    @Test
    void conflictWithoutAContentIdentityRethrowsWithoutALookup() {
        var request =
                new CreateMaterialTextRequest(
                        "Chapter", gradeSubject.getGradeSubjectId(), "some text");
        when(extractionService.extractText("some text")).thenReturn(ExtractionResult.failed());
        var conflict = new DataIntegrityViolationException("some other constraint");
        when(creationService.insertText(any(), anyString(), any(), any()))
                .thenThrow(conflict);

        assertThatExceptionOfType(DataIntegrityViolationException.class)
                .isThrownBy(() -> service.createFromText(principal, request))
                .isSameAs(conflict);
        verify(materialRepository, never()).findDedupeCandidate(any(), any(), anyString());
    }

    private Material winnerMaterial() {
        Material winner = new Material();
        winner.setMaterialId(UUID.randomUUID());
        winner.setTitle("Winning chapter");
        winner.setGradeSubject(gradeSubject);
        winner.setSourceType(MaterialSourceType.PASTE);
        winner.setExtractionStatus(ExtractionStatus.OK);
        winner.setExtractedChars(9);
        winner.setContentHash("hash");
        return winner;
    }

    private static MaterialDto dto(UUID materialId, boolean deduplicated) {
        return new MaterialDto(
                materialId,
                "Chapter",
                UUID.randomUUID(),
                "G8",
                "Grade 8",
                "Mathematics",
                "PASTE",
                "OK",
                9,
                "hash",
                deduplicated,
                null);
    }
}
