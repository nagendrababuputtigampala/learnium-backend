package com.learnium.learniumbackend.controller;

import com.learnium.learniumbackend.entity.response.master.ExamModeResponse;
import com.learnium.learniumbackend.service.master.ExamModeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ExamModeController {

    private final ExamModeService examModeService;

    @GetMapping("/exam-modes")
    public ResponseEntity<List<ExamModeResponse>> getExamModes(
            @RequestParam(required = false) UUID gradeId,
            @RequestParam(required = false) UUID subjectId,
            @RequestParam(required = false) UUID topicId
    ) {
        return ResponseEntity.ok(examModeService.getExamModes(gradeId, subjectId, topicId));
    }
}
