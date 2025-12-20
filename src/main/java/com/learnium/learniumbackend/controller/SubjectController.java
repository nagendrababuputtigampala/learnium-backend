package com.learnium.learniumbackend.controller;

import com.learnium.learniumbackend.entity.response.master.SubjectResponse;
import com.learnium.learniumbackend.service.master.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @GetMapping("/subjects")
    public ResponseEntity<List<SubjectResponse>> getSubjects(@RequestParam String gradeCode) {
        return ResponseEntity.ok(subjectService.getSubjectsByGrade(gradeCode));
    }
}
