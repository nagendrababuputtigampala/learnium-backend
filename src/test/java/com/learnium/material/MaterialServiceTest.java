package com.learnium.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learnium.api.dto.MaterialDtos.CreateMaterialTextRequest;
import com.learnium.common.error.BusinessException;
import com.learnium.model.content.ExtractionStatus;
import com.learnium.model.content.Material;
import com.learnium.model.master.Grade;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.master.Subject;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.content.MaterialRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.LearniumPrincipal;
import com.learnium.storage.StorageService;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service paths the HTTP layer cannot reach deterministically: an upload
 * stream that fails mid-read, an extraction that produced no text (so no
 * hash and no dedupe lookup), and a principal whose account row is gone.
 */
class MaterialServiceTest {

    private final MaterialRepository materialRepository = mock(MaterialRepository.class);
    private final GradeSubjectRepository gradeSubjectRepository =
            mock(GradeSubjectRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final ExtractionService extractionService = mock(ExtractionService.class);
    private final StorageService storageService = mock(StorageService.class);
    private final MaterialService service =
            new MaterialService(
                    materialRepository,
                    gradeSubjectRepository,
                    userAccountRepository,
                    extractionService,
                    storageService);

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

        Centre centre = new Centre();
        centre.setCentreId(centreId);
        UserAccount uploader = new UserAccount();
        uploader.setUserId(userId);
        uploader.setCentre(centre);
        when(userAccountRepository.findByCentreIdAndUserId(centreId, userId))
                .thenReturn(Optional.of(uploader));
        when(materialRepository.save(any(Material.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
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
    void failedExtractionPersistsWithoutHashOrDedupeLookup() {
        when(extractionService.extractText(anyString())).thenReturn(ExtractionResult.failed());

        var dto =
                service.createFromText(
                        principal,
                        new CreateMaterialTextRequest(
                                "Broken chapter", gradeSubject.getGradeSubjectId(), "some text"));

        assertThat(dto.extractionStatus()).isEqualTo("EXTRACTION_FAILED");
        assertThat(dto.contentHash()).isNull();
        assertThat(dto.deduplicated()).isFalse();
        verify(materialRepository, never())
                .findDedupeCandidate(any(), any(), anyString());
    }

    @Test
    void missingUploaderAccountIsSysNotFound() {
        when(userAccountRepository.findByCentreIdAndUserId(centreId, userId))
                .thenReturn(Optional.empty());
        when(extractionService.extractText(anyString()))
                .thenReturn(new ExtractionResult(ExtractionStatus.OK, "real text", 9));

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(
                        () ->
                                service.createFromText(
                                        principal,
                                        new CreateMaterialTextRequest(
                                                "Chapter",
                                                gradeSubject.getGradeSubjectId(),
                                                "real text")))
                .satisfies(e -> assertThat(e.getErrorCode().name()).isEqualTo("SYS_NOT_FOUND"));
    }
}
