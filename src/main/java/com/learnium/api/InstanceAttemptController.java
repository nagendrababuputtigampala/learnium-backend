package com.learnium.api;

import com.learnium.api.dto.AttemptAdminDtos.InstanceAttemptDto;
import com.learnium.api.dto.IntegrityDtos.IntegrityEventDto;
import com.learnium.exam.AttemptAdminService;
import com.learnium.security.CurrentPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('CENTRE_ADMIN','TEACHER')")
public class InstanceAttemptController {
    private final AttemptAdminService service;

    public InstanceAttemptController(AttemptAdminService service) {
        this.service = service;
    }

    @GetMapping("/instances/{instanceId}/attempts")
    public ResponseEntity<List<InstanceAttemptDto>> attempts(@PathVariable UUID instanceId) {
        return ResponseEntity.ok(service.attempts(CurrentPrincipal.require(), instanceId));
    }

    @GetMapping("/attempts/{attemptId}/integrity-events")
    public ResponseEntity<List<IntegrityEventDto>> timeline(@PathVariable UUID attemptId) {
        return ResponseEntity.ok(service.timeline(CurrentPrincipal.require(), attemptId));
    }

    @PostMapping("/attempts/{attemptId}/invalidate")
    public ResponseEntity<InstanceAttemptDto> invalidate(@PathVariable UUID attemptId) {
        return ResponseEntity.ok(service.invalidate(CurrentPrincipal.require(), attemptId));
    }
}
