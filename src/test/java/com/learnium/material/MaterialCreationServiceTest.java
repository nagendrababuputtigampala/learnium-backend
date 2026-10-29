package com.learnium.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * The transactional creation step: dedupe lookup, identity maintenance and
 * the forced flush that makes the V11 unique index the arbiter of races.
 */
class MaterialCreationServiceTest {

    private final MaterialRepository materialRepository = mock(MaterialRepository.class);
    private final GradeSubjectRepository gradeSubjectRepository =
            mock(GradeSubjectRepository.class);
    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final StorageService storageService = mock(StorageService.class);
    private final MaterialCreationService service =
            new MaterialCreationService(
                    materialRepository,
                    gradeSubjectRepository,
                    userAccountRepository,
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
        when(materialRepository.findDedupeCandidate(any(), any(), anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void insertTextPersistsWithTheDedupeIdentitySet() {
        var extraction = new ExtractionResult(ExtractionStatus.OK, "real text", 9);

        var dto =
                service.insertText(
                        principal, "  Chapter  ", gradeSubject.getGradeSubjectId(), extraction);

        assertThat(dto.deduplicated()).isFalse();
        assertThat(dto.contentHash()).isEqualTo(extraction.contentHash());
        ArgumentCaptor<Material> saved = ArgumentCaptor.forClass(Material.class);
        verify(materialRepository).save(saved.capture());
        assertThat(saved.getValue().getTitle()).isEqualTo("Chapter");
        assertThat(saved.getValue().getDedupeHash()).isEqualTo(extraction.contentHash());
        assertThat(saved.getValue().getContentHash()).isEqualTo(extraction.contentHash());
        verify(materialRepository).flush();
    }

    @Test
    void insertTextDedupeHitReturnsTheExistingMaterialWithoutSaving() {
        var extraction = new ExtractionResult(ExtractionStatus.OK, "real text", 9);
        Material existing = new Material();
        existing.setMaterialId(UUID.randomUUID());
        existing.setTitle("Earlier chapter");
        existing.setGradeSubject(gradeSubject);
        existing.setSourceType(com.learnium.model.content.MaterialSourceType.PASTE);
        existing.setExtractionStatus(ExtractionStatus.OK);
        when(materialRepository.findDedupeCandidate(centreId, userId, extraction.contentHash()))
                .thenReturn(Optional.of(existing));

        var dto =
                service.insertText(
                        principal, "Chapter", gradeSubject.getGradeSubjectId(), extraction);

        assertThat(dto.deduplicated()).isTrue();
        assertThat(dto.materialId()).isEqualTo(existing.getMaterialId());
        verify(materialRepository, never()).save(any(Material.class));
    }

    @Test
    void failedExtractionPersistsWithoutHashOrDedupeLookup() {
        var dto =
                service.insertText(
                        principal,
                        "Broken chapter",
                        gradeSubject.getGradeSubjectId(),
                        ExtractionResult.failed());

        assertThat(dto.extractionStatus()).isEqualTo("EXTRACTION_FAILED");
        assertThat(dto.contentHash()).isNull();
        assertThat(dto.deduplicated()).isFalse();
        verify(materialRepository, never()).findDedupeCandidate(any(), any(), anyString());
        ArgumentCaptor<Material> saved = ArgumentCaptor.forClass(Material.class);
        verify(materialRepository).save(saved.capture());
        assertThat(saved.getValue().getDedupeHash()).isNull();
    }

    @Test
    void insertUploadStoresTheFileAndFlushes() {
        var extraction = new ExtractionResult(ExtractionStatus.OK, "real text", 9);
        byte[] bytes = "%PDF-1.7 body".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(storageService.store(any(), any(), anyString(), any(byte[].class)))
                .thenReturn("key/1");

        var dto =
                service.insertUpload(
                        principal,
                        "Chapter",
                        gradeSubject.getGradeSubjectId(),
                        "chapter.pdf",
                        bytes,
                        extraction);

        assertThat(dto.deduplicated()).isFalse();
        ArgumentCaptor<Material> saved = ArgumentCaptor.forClass(Material.class);
        verify(materialRepository).save(saved.capture());
        verify(storageService)
                .store(centreId, saved.getValue().getMaterialId(), "chapter.pdf", bytes);
        assertThat(saved.getValue().getStorageKey()).isEqualTo("key/1");
        verify(materialRepository).flush();
    }

    @Test
    void insertUploadDedupeHitStoresNothing() {
        var extraction = new ExtractionResult(ExtractionStatus.OK, "real text", 9);
        Material existing = new Material();
        existing.setMaterialId(UUID.randomUUID());
        existing.setTitle("Earlier chapter");
        existing.setGradeSubject(gradeSubject);
        existing.setSourceType(com.learnium.model.content.MaterialSourceType.PDF);
        existing.setExtractionStatus(ExtractionStatus.OK);
        when(materialRepository.findDedupeCandidate(centreId, userId, extraction.contentHash()))
                .thenReturn(Optional.of(existing));

        var dto =
                service.insertUpload(
                        principal,
                        "Chapter",
                        gradeSubject.getGradeSubjectId(),
                        "chapter.pdf",
                        new byte[0],
                        extraction);

        assertThat(dto.deduplicated()).isTrue();
        verify(materialRepository, never()).save(any(Material.class));
        verify(storageService, never()).store(any(), any(), anyString(), any());
    }

    @Test
    void missingUploaderAccountIsSysNotFound() {
        when(userAccountRepository.findByCentreIdAndUserId(centreId, userId))
                .thenReturn(Optional.empty());
        var extraction = new ExtractionResult(ExtractionStatus.OK, "real text", 9);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(
                        () ->
                                service.insertText(
                                        principal,
                                        "Chapter",
                                        gradeSubject.getGradeSubjectId(),
                                        extraction))
                .satisfies(e -> assertThat(e.getErrorCode().name()).isEqualTo("SYS_NOT_FOUND"));
    }

    @Test
    void missingGradeSubjectIsSysNotFound() {
        UUID unknown = UUID.randomUUID();
        when(gradeSubjectRepository.findById(unknown)).thenReturn(Optional.empty());
        var extraction = new ExtractionResult(ExtractionStatus.OK, "real text", 9);

        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.insertText(principal, "Chapter", unknown, extraction))
                .satisfies(e -> assertThat(e.getErrorCode().name()).isEqualTo("SYS_NOT_FOUND"));
    }
}
