package com.learnium.material;

import com.learnium.api.dto.MaterialDtos.CreateMaterialTextRequest;
import com.learnium.api.dto.MaterialDtos.MaterialDetailDto;
import com.learnium.api.dto.MaterialDtos.MaterialDto;
import com.learnium.common.api.PageResponse;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.content.Material;
import com.learnium.model.content.MaterialSourceType;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.content.MaterialRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.LearniumPrincipal;
import com.learnium.storage.StorageService;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Chapter materials: upload a PDF or paste text, extract and classify the
 * text, and keep the result visible to its creator and the centre admin —
 * nobody else, not even another teacher in the same centre.
 *
 * <p>Tenancy always comes from the token. Content identity is the SHA-256
 * of the COMPLETE extracted text, computed only when extraction produced
 * text (two different scanned PDFs both extract to nothing and must never
 * dedupe into each other). Deduplication is scoped to the same uploader: a
 * match returns the teacher's own earlier material and stores nothing new.
 */
@Service
public class MaterialService {

    /** Upload cap for chapter PDFs; the servlet limit sits just above it. */
    static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

    private static final byte[] PDF_HEADER = {'%', 'P', 'D', 'F', '-'};

    private static boolean startsWithPdfHeader(byte[] bytes) {
        return bytes.length >= PDF_HEADER.length
                && Arrays.equals(
                        bytes, 0, PDF_HEADER.length, PDF_HEADER, 0, PDF_HEADER.length);
    }

    private final MaterialRepository materialRepository;
    private final GradeSubjectRepository gradeSubjectRepository;
    private final UserAccountRepository userAccountRepository;
    private final ExtractionService extractionService;
    private final StorageService storageService;

    public MaterialService(
            MaterialRepository materialRepository,
            GradeSubjectRepository gradeSubjectRepository,
            UserAccountRepository userAccountRepository,
            ExtractionService extractionService,
            StorageService storageService) {
        this.materialRepository = materialRepository;
        this.gradeSubjectRepository = gradeSubjectRepository;
        this.userAccountRepository = userAccountRepository;
        this.extractionService = extractionService;
        this.storageService = storageService;
    }

    @Transactional
    public MaterialDto createFromUpload(
            LearniumPrincipal principal, MultipartFile file, String title, UUID gradeSubjectId) {
        validateTitle(title);
        if (gradeSubjectId == null) {
            throw new BusinessException(ErrorCode.VAL_FAILED, "gradeSubjectId is required");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.MAT_INVALID_FILE, "A PDF file is required");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new BusinessException(ErrorCode.MAT_TOO_LARGE);
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new BusinessException(ErrorCode.MAT_INVALID_FILE, "Only PDF files are accepted");
        }
        GradeSubject gradeSubject = requireGradeSubject(gradeSubjectId);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.MAT_INVALID_FILE, "The file could not be read");
        }
        // Magic bytes, not the filename, decide: anything that does not
        // start with the PDF header is rejected before it is extracted,
        // stored or persisted. A file WITH the header but a corrupt body
        // still flows through and persists as EXTRACTION_FAILED.
        if (!startsWithPdfHeader(bytes)) {
            throw new BusinessException(
                    ErrorCode.MAT_INVALID_FILE, "The uploaded file is not a PDF");
        }
        ExtractionResult extraction = extractionService.extractPdf(bytes);
        String contentHash = contentHashOf(extraction);
        if (contentHash != null) {
            var existing =
                    materialRepository.findDedupeCandidate(
                            principal.centreId(), principal.userId(), contentHash);
            if (existing.isPresent()) {
                return toDto(existing.get(), true);
            }
        }
        Material material = newMaterial(principal, title, gradeSubject, MaterialSourceType.PDF);
        applyExtraction(material, extraction, contentHash);
        // Persist first so the generated id anchors the storage key, then
        // store the file inside the same transaction: if storing fails,
        // the insert rolls back with it — a committed row never points at
        // a missing file. (Pre-assigning the id instead made save() take
        // the merge path and trip the @Version check.)
        material = materialRepository.save(material);
        material.setStorageKey(
                storageService.store(
                        principal.centreId(), material.getMaterialId(), filename, bytes));
        return toDto(material, false);
    }

    @Transactional
    public MaterialDto createFromText(
            LearniumPrincipal principal, CreateMaterialTextRequest request) {
        GradeSubject gradeSubject = requireGradeSubject(request.gradeSubjectId());
        ExtractionResult extraction = extractionService.extractText(request.text());
        String contentHash = contentHashOf(extraction);
        if (contentHash != null) {
            var existing =
                    materialRepository.findDedupeCandidate(
                            principal.centreId(), principal.userId(), contentHash);
            if (existing.isPresent()) {
                return toDto(existing.get(), true);
            }
        }
        Material material =
                newMaterial(principal, request.title(), gradeSubject, MaterialSourceType.PASTE);
        applyExtraction(material, extraction, contentHash);
        return toDto(materialRepository.save(material), false);
    }

    @Transactional(readOnly = true)
    public PageResponse<MaterialDto> list(LearniumPrincipal principal, Pageable pageable) {
        var page =
                principal.role() == UserRole.CENTRE_ADMIN
                        ? materialRepository.findVisiblePageByCentreId(
                                principal.centreId(), pageable)
                        : materialRepository.findVisiblePageByCentreIdAndUploader(
                                principal.centreId(), principal.userId(), pageable);
        return PageResponse.from(page, material -> toDto(material, false));
    }

    @Transactional(readOnly = true)
    public MaterialDetailDto get(LearniumPrincipal principal, UUID materialId) {
        return toDetailDto(requireVisible(principal, materialId));
    }

    /**
     * Soft delete: the row stays (provenance, in-flight work) with a
     * deletion marker; the stored file is removed by the deferred cleanup,
     * not here — database and object storage cannot commit atomically.
     */
    @Transactional
    public void delete(LearniumPrincipal principal, UUID materialId) {
        Material material = requireVisible(principal, materialId);
        material.setDeletedAt(Instant.now());
        materialRepository.save(material);
    }

    /**
     * Resolves a material the caller may see: creator or centre admin, in
     * the caller's centre, not soft-deleted. Everything else is a 404 — a
     * material's existence is never leaked across teachers or centres.
     */
    private Material requireVisible(LearniumPrincipal principal, UUID materialId) {
        Material material =
                materialRepository
                        .findVisibleById(materialId, principal.centreId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.MAT_NOT_FOUND));
        if (principal.role() == UserRole.TEACHER
                && !material.getUploadedBy().getUserId().equals(principal.userId())) {
            throw new BusinessException(ErrorCode.MAT_NOT_FOUND);
        }
        return material;
    }

    private Material newMaterial(
            LearniumPrincipal principal,
            String title,
            GradeSubject gradeSubject,
            MaterialSourceType sourceType) {
        UserAccount uploader =
                userAccountRepository
                        .findByCentreIdAndUserId(principal.centreId(), principal.userId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.SYS_NOT_FOUND));
        Material material = new Material();
        material.setCentre(uploader.getCentre());
        material.setUploadedBy(uploader);
        material.setGradeSubject(gradeSubject);
        material.setTitle(title.trim());
        material.setSourceType(sourceType);
        return material;
    }

    private GradeSubject requireGradeSubject(UUID gradeSubjectId) {
        return gradeSubjectRepository
                .findById(gradeSubjectId)
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        ErrorCode.SYS_NOT_FOUND, "Grade subject not found"));
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank() || title.trim().length() > 200) {
            throw new BusinessException(
                    ErrorCode.VAL_FAILED, "Title must be between 1 and 200 characters");
        }
    }

    /**
     * Content identity is the extraction's SHA-256 of the COMPLETE text —
     * never of the truncated store, or two chapters sharing a capped prefix
     * but differing later would collapse into one identity. It is null when
     * extraction produced no text (see {@link ExtractionResult}).
     */
    private static String contentHashOf(ExtractionResult extraction) {
        return extraction.contentHash();
    }

    private static void applyExtraction(
            Material material, ExtractionResult extraction, String contentHash) {
        material.setExtractedText(extraction.storedText());
        material.setExtractedChars(extraction.storedChars());
        material.setExtractionStatus(extraction.status());
        material.setContentHash(contentHash);
    }

    private static MaterialDto toDto(Material material, boolean deduplicated) {
        return new MaterialDto(
                material.getMaterialId(),
                material.getTitle(),
                material.getGradeSubject().getGradeSubjectId(),
                material.getGradeSubject().getGrade().getCode(),
                material.getGradeSubject().getGrade().getName(),
                material.getGradeSubject().getSubject().getSubjectName(),
                material.getSourceType().name(),
                material.getExtractionStatus() == null
                        ? null
                        : material.getExtractionStatus().name(),
                material.getExtractedChars(),
                material.getContentHash(),
                deduplicated,
                material.getCreatedAt());
    }

    private static MaterialDetailDto toDetailDto(Material material) {
        return new MaterialDetailDto(
                material.getMaterialId(),
                material.getTitle(),
                material.getGradeSubject().getGradeSubjectId(),
                material.getGradeSubject().getGrade().getCode(),
                material.getGradeSubject().getGrade().getName(),
                material.getGradeSubject().getSubject().getSubjectName(),
                material.getSourceType().name(),
                material.getExtractionStatus() == null
                        ? null
                        : material.getExtractionStatus().name(),
                material.getExtractedChars(),
                material.getContentHash(),
                material.getExtractedText(),
                material.getCreatedAt());
    }
}
