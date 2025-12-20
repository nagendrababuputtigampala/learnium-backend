package com.learnium.learniumbackend.controller;

import com.learnium.learniumbackend.entity.response.master.GradeResponse;
import com.learnium.learniumbackend.service.master.GradeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class GradeController {

    private final GradeService gradeService;

    @GetMapping("/grades")
    public ResponseEntity<List<GradeResponse>> getGrades() {
        return ResponseEntity.ok(gradeService.getAllGrades());
    }
}