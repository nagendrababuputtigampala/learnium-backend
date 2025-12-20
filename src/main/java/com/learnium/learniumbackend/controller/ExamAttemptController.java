package com.learnium.learniumbackend.controller;
import com.learnium.learniumbackend.entity.request.exam.SubmitExamAttemptRequest;
import com.learnium.learniumbackend.entity.response.exam.ExamAttemptReviewResponse;
import com.learnium.learniumbackend.entity.response.exam.SubmitExamAttemptResponse;
import com.learnium.learniumbackend.service.exam.ExamAttemptReviewService;
import com.learnium.learniumbackend.service.exam.ExamAttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ExamAttemptController {

    private final ExamAttemptService examAttemptService;

    private final ExamAttemptReviewService reviewService;

    @PostMapping("/exam-attempts/{attemptId}/submit")
    public ResponseEntity<SubmitExamAttemptResponse> submit(
            @PathVariable UUID attemptId,
            @RequestBody SubmitExamAttemptRequest request
    ) {
        return ResponseEntity.ok(examAttemptService.submit(attemptId, request));
    }

    @GetMapping("/exam-attempts/{id}/review")
    public ExamAttemptReviewResponse getAttemptReview(@PathVariable("id") UUID attemptId) {
        return reviewService.getReview(attemptId);
    }
}
