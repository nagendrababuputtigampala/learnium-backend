package com.learnium.api;

import com.learnium.api.dto.MaterialDtos.CreateMaterialTextRequest;
import com.learnium.api.dto.MaterialDtos.MaterialDetailDto;
import com.learnium.api.dto.MaterialDtos.MaterialDto;
import com.learnium.common.api.PageResponse;
import com.learnium.material.MaterialService;
import com.learnium.security.CurrentPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Chapter materials (M3): PDF upload or pasted text in, extracted and
 * classified text out. Staff only — students never touch source material.
 * Multipart parameters are bound as optional so the service can answer
 * with the standard error envelope (LRN-MAT-*) instead of a framework
 * binding failure.
 */
@RestController
@RequestMapping("/api/v1/materials")
@PreAuthorize("hasAnyRole('CENTRE_ADMIN', 'TEACHER')")
public class MaterialController {

    private final MaterialService materialService;

    public MaterialController(MaterialService materialService) {
        this.materialService = materialService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MaterialDto> createFromUpload(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "gradeSubjectId", required = false) UUID gradeSubjectId) {
        MaterialDto created =
                materialService.createFromUpload(
                        CurrentPrincipal.require(), file, title, gradeSubjectId);
        return ResponseEntity.status(created.deduplicated() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(created);
    }

    @PostMapping("/text")
    public ResponseEntity<MaterialDto> createFromText(
            @Valid @RequestBody CreateMaterialTextRequest request) {
        MaterialDto created = materialService.createFromText(CurrentPrincipal.require(), request);
        return ResponseEntity.status(created.deduplicated() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(created);
    }

    @GetMapping
    public ResponseEntity<PageResponse<MaterialDto>> list(Pageable pageable) {
        return ResponseEntity.ok(materialService.list(CurrentPrincipal.require(), pageable));
    }

    @GetMapping("/{materialId}")
    public ResponseEntity<MaterialDetailDto> get(@PathVariable UUID materialId) {
        return ResponseEntity.ok(materialService.get(CurrentPrincipal.require(), materialId));
    }

    @DeleteMapping("/{materialId}")
    public ResponseEntity<Void> delete(@PathVariable UUID materialId) {
        materialService.delete(CurrentPrincipal.require(), materialId);
        return ResponseEntity.noContent().build();
    }
}
