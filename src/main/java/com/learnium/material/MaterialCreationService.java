package com.learnium.material;

import com.learnium.api.dto.MaterialDtos.MaterialDto;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.content.Material;
import com.learnium.model.content.MaterialSourceType;
import com.learnium.model.master.GradeSubject;
import com.learnium.model.user.UserAccount;
import com.learnium.repository.content.MaterialRepository;
import com.learnium.repository.master.GradeSubjectRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.LearniumPrincipal;
import com.learnium.storage.StorageService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of material creation, kept apart from
 * {@link MaterialService} on purpose: a dedupe-identity violation must roll
 * back HERE, inside this transaction, so the caller can recover by
 * returning the winning row. After a constraint violation the surrounding
 * transaction is unusable (PostgreSQL aborts it outright), so
 * insert-and-recover can never live in a single transaction — the same
 * reason M2's auth fixes moved state changes into independent
 * transactions.
 *
 * <p>The dedupe lookup happens inside this transaction, immediately before
 * the insert, and the insert is flushed before returning, so the unique
 * index on the dedupe identity (V11) — not the lookup — is the final
 * arbiter when two identical uploads race.
 */
@Service
public class MaterialCreationService {

    private final MaterialRepository materialRepository;
    private final GradeSubjectRepository gradeSubjectRepository;
    private final UserAccountRepository userAccountRepository;
    private final StorageService storageService;

    public MaterialCreationService(
            MaterialRepository materialRepository,
            GradeSubjectRepository gradeSubjectRepository,
            UserAccountRepository userAccountRepository,
            StorageService storageService) {
        this.materialRepository = materialRepository;
        this.gradeSubjectRepository = gradeSubjectRepository;
        this.userAccountRepository = userAccountRepository;
        this.storageService = storageService;
    }

    @Transactional
    public MaterialDto insertUpload(
            LearniumPrincipal principal,
            String title,
            UUID gradeSubjectId,
            String filename,
            byte[] bytes,
            ExtractionResult extraction) {
        var existing = dedupeCandidate(principal, extraction);
        if (existing != null) {
            return MaterialService.toDto(existing, true);
        }
        Material material =
                newMaterial(principal, title, gradeSubjectId, MaterialSourceType.PDF, extraction);
        // Persist first so the generated id anchors the storage key, then
        // store the file inside the same transaction: if storing fails,
        // the insert rolls back with it — a committed row never points at
        // a missing file. (Pre-assigning the id instead made save() take
        // the merge path and trip the @Version check.)
        material = materialRepository.save(material);
        material.setStorageKey(
                storageService.store(
                        principal.centreId(), material.getMaterialId(), filename, bytes));
        // Force the insert out so a dedupe-identity violation surfaces
        // here, as a DataIntegrityViolationException the caller recovers
        // from, instead of at some later commit it cannot.
        materialRepository.flush();
        return MaterialService.toDto(material, false);
    }

    @Transactional
    public MaterialDto insertText(
            LearniumPrincipal principal,
            String title,
            UUID gradeSubjectId,
            ExtractionResult extraction) {
        var existing = dedupeCandidate(principal, extraction);
        if (existing != null) {
            return MaterialService.toDto(existing, true);
        }
        Material material =
                newMaterial(principal, title, gradeSubjectId, MaterialSourceType.PASTE, extraction);
        material = materialRepository.save(material);
        materialRepository.flush();
        return MaterialService.toDto(material, false);
    }

    /**
     * The same-uploader dedupe match for this content, or null — either
     * because the extraction produced no identity (scanned/failed: no hash)
     * or because this uploader has not stored this content before.
     */
    private Material dedupeCandidate(LearniumPrincipal principal, ExtractionResult extraction) {
        if (extraction.contentHash() == null) {
            return null;
        }
        return materialRepository
                .findDedupeCandidate(
                        principal.centreId(), principal.userId(), extraction.contentHash())
                .orElse(null);
    }

    private Material newMaterial(
            LearniumPrincipal principal,
            String title,
            UUID gradeSubjectId,
            MaterialSourceType sourceType,
            ExtractionResult extraction) {
        UserAccount uploader =
                userAccountRepository
                        .findByCentreIdAndUserId(principal.centreId(), principal.userId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.SYS_NOT_FOUND));
        GradeSubject gradeSubject =
                gradeSubjectRepository
                        .findById(gradeSubjectId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                ErrorCode.SYS_NOT_FOUND,
                                                "Grade subject not found"));
        Material material = new Material();
        material.setCentre(uploader.getCentre());
        material.setUploadedBy(uploader);
        material.setGradeSubject(gradeSubject);
        material.setTitle(title.trim());
        material.setSourceType(sourceType);
        material.setExtractedText(extraction.storedText());
        material.setExtractedChars(extraction.storedChars());
        material.setExtractionStatus(extraction.status());
        material.setContentHash(extraction.contentHash());
        // The identity lives exactly as long as the material is active;
        // soft delete clears it (MaterialService.delete) so the same
        // chapter can be uploaded again afterwards.
        material.setDedupeHash(extraction.contentHash());
        return material;
    }
}
