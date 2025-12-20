package com.learnium.learniumbackend.controller;

import com.learnium.learniumbackend.entity.response.content.ExamTemplateResponse;
import com.learnium.learniumbackend.entity.response.content.TestDetailResponse;
import com.learnium.learniumbackend.service.content.ExamTemplateService;
import com.learnium.learniumbackend.service.content.TestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TestController {

    private final ExamTemplateService examTemplateService;

    private final TestService testService;

    @GetMapping("/tests")
    public ResponseEntity<List<ExamTemplateResponse>> getTests(
            @RequestParam UUID gradeId,
            @RequestParam UUID subjectId,
            @RequestParam UUID topicId,
            @RequestParam UUID examModeId
    ) {
        return ResponseEntity.ok(examTemplateService.getTests(gradeId, subjectId, topicId, examModeId));
    }

    @GetMapping("/tests/{templateId}")
    public ResponseEntity<TestDetailResponse> getTest(@PathVariable UUID templateId) {
        return ResponseEntity.ok(testService.getTest(templateId));
    }
}