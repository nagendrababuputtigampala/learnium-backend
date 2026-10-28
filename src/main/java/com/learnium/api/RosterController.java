package com.learnium.api;

import com.learnium.admin.RosterService;
import com.learnium.api.dto.BatchDtos.AddStudentRequest;
import com.learnium.api.dto.BatchDtos.AddStudentResponse;
import com.learnium.api.dto.BatchDtos.BulkAddStudentsRequest;
import com.learnium.api.dto.BatchDtos.ResetPinResponse;
import com.learnium.api.dto.BatchDtos.RosterStudentDto;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Batch rosters. Staff-only at the door; {@code BatchAccessGuard} inside the
 * service narrows teachers to their assigned batches.
 */
@RestController
@RequestMapping("/api/v1/batches/{batchId}/students")
@PreAuthorize("hasAnyRole('CENTRE_ADMIN', 'TEACHER')")
public class RosterController {

    private final RosterService rosterService;

    public RosterController(RosterService rosterService) {
        this.rosterService = rosterService;
    }

    @GetMapping
    public ResponseEntity<List<RosterStudentDto>> listStudents(@PathVariable UUID batchId) {
        return ResponseEntity.ok(rosterService.listStudents(CurrentPrincipal.require(), batchId));
    }

    @PostMapping
    public ResponseEntity<AddStudentResponse> addStudent(
            @PathVariable UUID batchId, @Valid @RequestBody AddStudentRequest request) {
        AddStudentResponse created =
                rosterService.addStudent(CurrentPrincipal.require(), batchId, request.fullName());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/bulk")
    public ResponseEntity<List<AddStudentResponse>> bulkAddStudents(
            @PathVariable UUID batchId, @Valid @RequestBody BulkAddStudentsRequest request) {
        List<AddStudentResponse> created =
                rosterService.bulkAddStudents(CurrentPrincipal.require(), batchId, request.fullNames());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{membershipId}/reset-pin")
    public ResponseEntity<ResetPinResponse> resetPin(
            @PathVariable UUID batchId, @PathVariable UUID membershipId) {
        return ResponseEntity.ok(
                rosterService.resetPin(CurrentPrincipal.require(), batchId, membershipId));
    }

    @DeleteMapping("/{membershipId}")
    public ResponseEntity<Void> deactivateStudent(
            @PathVariable UUID batchId, @PathVariable UUID membershipId) {
        rosterService.deactivateStudent(CurrentPrincipal.require(), batchId, membershipId);
        return ResponseEntity.noContent().build();
    }
}
