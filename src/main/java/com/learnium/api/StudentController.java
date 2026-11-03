package com.learnium.api;

import com.learnium.api.dto.IntegrityDtos.*;
import com.learnium.api.dto.StudentDtos.*;
import com.learnium.exam.IntegrityService;
import com.learnium.exam.StudentAttemptService;
import com.learnium.exam.StudentTestService;
import com.learnium.exam.StudentDashboardService;
import com.learnium.api.dto.StudentDashboardDtos.DashboardDto;
import com.learnium.security.CurrentPrincipal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {
    private final StudentTestService tests;
    private final StudentAttemptService attempts;
    private final StudentDashboardService dashboard;
    private final IntegrityService integrity;
    public StudentController(StudentTestService tests, StudentAttemptService attempts, StudentDashboardService dashboard,
            IntegrityService integrity) {
        this.tests = tests; this.attempts = attempts; this.dashboard = dashboard; this.integrity = integrity;
    }
    @GetMapping("/tests")
    public ResponseEntity<List<StudentTestDto>> tests() {
        return ResponseEntity.ok(tests.tests(CurrentPrincipal.require()));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDto> dashboard() {
        return ResponseEntity.ok(dashboard.dashboard(CurrentPrincipal.require()));
    }

    @PostMapping("/tests/{instanceId}/attempts")
    public ResponseEntity<AttemptDto> start(@PathVariable UUID instanceId) {
        return ResponseEntity.ok(attempts.start(CurrentPrincipal.require(), instanceId));
    }

    @GetMapping("/attempts/{attemptId}")
    public ResponseEntity<AttemptDto> attempt(@PathVariable UUID attemptId) {
        return ResponseEntity.ok(attempts.attempt(CurrentPrincipal.require(), attemptId));
    }

    @PutMapping("/attempts/{attemptId}/answers/{itemId}")
    public ResponseEntity<SavedAnswerResponse> save(@PathVariable UUID attemptId, @PathVariable UUID itemId,
            @Valid @RequestBody SaveAnswerRequest request) {
        return ResponseEntity.ok(attempts.save(CurrentPrincipal.require(), attemptId, itemId, request.answer()));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public ResponseEntity<AttemptResultDto> submit(@PathVariable UUID attemptId) {
        return ResponseEntity.ok(attempts.submit(CurrentPrincipal.require(), attemptId));
    }

    @GetMapping("/attempts/{attemptId}/result")
    public ResponseEntity<AttemptResultDto> result(@PathVariable UUID attemptId) {
        return ResponseEntity.ok(attempts.result(CurrentPrincipal.require(), attemptId));
    }

    @GetMapping("/tests/{instanceId}/practice")
    public ResponseEntity<PracticeDto> practice(@PathVariable UUID instanceId) {
        return ResponseEntity.ok(attempts.practice(CurrentPrincipal.require(), instanceId));
    }

    @PostMapping("/attempts/{attemptId}/integrity-events")
    public ResponseEntity<IntegrityEventsResponse> integrityEvents(@PathVariable UUID attemptId,
            @RequestBody IntegrityEventsRequest request) {
        return ResponseEntity.ok(new IntegrityEventsResponse(
                integrity.record(CurrentPrincipal.require(), attemptId, request == null ? null : request.events())));
    }
}
