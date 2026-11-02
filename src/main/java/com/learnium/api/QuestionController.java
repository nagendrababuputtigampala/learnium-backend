package com.learnium.api;

import com.learnium.api.dto.QuestionDtos.*;
import com.learnium.common.api.PageResponse;
import com.learnium.question.QuestionReviewService;
import com.learnium.security.CurrentPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/questions")
@PreAuthorize("hasAnyRole('CENTRE_ADMIN', 'TEACHER')")
public class QuestionController {
    private final QuestionReviewService service;
    public QuestionController(QuestionReviewService service) { this.service = service; }

    @GetMapping
    public PageResponse<QuestionDto> list(@RequestParam(defaultValue = "DRAFT") String status,
            @RequestParam(required = false) UUID materialId, @RequestParam(required = false) UUID gradeSubjectId,
            @RequestParam(required = false) UUID gradeId, @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID topicId, @RequestParam(required = false) String type,
            @RequestParam(required = false) String difficulty, Pageable pageable) {
        return service.list(CurrentPrincipal.require(), status, materialId, gradeSubjectId,
                gradeId, subjectId, topicId, type, difficulty, pageable);
    }

    @GetMapping("/{id}")
    public QuestionDto get(@PathVariable UUID id) { return service.get(CurrentPrincipal.require(), id); }

    @PutMapping("/{id}")
    public QuestionDto edit(@PathVariable UUID id, @Valid @RequestBody EditRequest body) {
        return service.edit(CurrentPrincipal.require(), id, body);
    }

    @PostMapping("/{id}/approve")
    public QuestionDto approve(@PathVariable UUID id, @Valid @RequestBody DecisionRequest body) {
        return service.decide(CurrentPrincipal.require(), id, body.revision(), true);
    }

    @PostMapping("/{id}/reject")
    public QuestionDto reject(@PathVariable UUID id, @Valid @RequestBody DecisionRequest body) {
        return service.decide(CurrentPrincipal.require(), id, body.revision(), false);
    }

    @PostMapping("/{id}/forks")
    public ResponseEntity<QuestionDto> fork(@PathVariable UUID id) {
        return ResponseEntity.status(201).body(service.fork(CurrentPrincipal.require(), id));
    }
}
