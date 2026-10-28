package com.learnium.api;

import com.learnium.admin.BatchService;
import com.learnium.api.dto.BatchDtos.AssignTeacherRequest;
import com.learnium.api.dto.BatchDtos.BatchDto;
import com.learnium.api.dto.BatchDtos.CreateBatchRequest;
import com.learnium.security.CurrentPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Batches: admin creates and staffs them; staff list what they may see. */
@RestController
@RequestMapping("/api/v1")
public class BatchController {

    private final BatchService batchService;

    public BatchController(BatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping("/admin/batches")
    @PreAuthorize("hasRole('CENTRE_ADMIN')")
    public ResponseEntity<BatchDto> createBatch(@Valid @RequestBody CreateBatchRequest request) {
        BatchDto created = batchService.createBatch(CurrentPrincipal.require(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/batches")
    @PreAuthorize("hasAnyRole('CENTRE_ADMIN', 'TEACHER')")
    public ResponseEntity<List<BatchDto>> listBatches() {
        return ResponseEntity.ok(batchService.listBatches(CurrentPrincipal.require()));
    }

    @PutMapping("/admin/batches/{batchId}/teacher")
    @PreAuthorize("hasRole('CENTRE_ADMIN')")
    public ResponseEntity<BatchDto> assignTeacher(
            @PathVariable UUID batchId, @Valid @RequestBody AssignTeacherRequest request) {
        return ResponseEntity.ok(
                batchService.assignTeacher(CurrentPrincipal.require(), batchId, request.userId()));
    }

    @DeleteMapping("/admin/batches/{batchId}/teacher/{userId}")
    @PreAuthorize("hasRole('CENTRE_ADMIN')")
    public ResponseEntity<Void> unassignTeacher(@PathVariable UUID batchId, @PathVariable UUID userId) {
        batchService.unassignTeacher(CurrentPrincipal.require(), batchId, userId);
        return ResponseEntity.noContent().build();
    }
}
