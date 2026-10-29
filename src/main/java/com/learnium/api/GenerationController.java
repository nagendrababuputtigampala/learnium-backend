package com.learnium.api;

import com.learnium.api.dto.GenerationDtos.GenerateRequest;
import com.learnium.api.dto.GenerationDtos.GeneratedQuestionDto;
import com.learnium.api.dto.GenerationDtos.GenerationJobDetailDto;
import com.learnium.api.dto.GenerationDtos.GenerationJobDto;
import com.learnium.common.api.PageResponse;
import com.learnium.generation.GenerationJobService;
import com.learnium.security.CurrentPrincipal;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI generation (M3 branch 2): queue a job from a material, poll it, and
 * preview its drafts. Staff only — students never see source material or
 * unapproved drafts. Creation answers 202: the job executes in the
 * background worker, and the response is the frozen job as created.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('CENTRE_ADMIN', 'TEACHER')")
public class GenerationController {

    private final GenerationJobService generationJobService;

    public GenerationController(GenerationJobService generationJobService) {
        this.generationJobService = generationJobService;
    }

    @PostMapping("/materials/{materialId}/generate")
    public ResponseEntity<GenerationJobDto> generate(
            @PathVariable UUID materialId, @RequestBody GenerateRequest request) {
        GenerationJobDto job =
                generationJobService.createJob(CurrentPrincipal.require(), materialId, request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
    }

    @GetMapping("/generation-jobs/{jobId}")
    public ResponseEntity<GenerationJobDetailDto> getJob(@PathVariable UUID jobId) {
        return ResponseEntity.ok(generationJobService.getJob(CurrentPrincipal.require(), jobId));
    }

    @GetMapping("/materials/{materialId}/questions")
    public ResponseEntity<PageResponse<GeneratedQuestionDto>> listQuestions(
            @PathVariable UUID materialId, Pageable pageable) {
        return ResponseEntity.ok(
                generationJobService.listMaterialQuestions(
                        CurrentPrincipal.require(), materialId, pageable));
    }
}
