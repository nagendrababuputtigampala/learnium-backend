package com.learnium.api;

import com.learnium.api.dto.StudentDtos.StudentTestDto;
import com.learnium.exam.StudentTestService;
import com.learnium.security.CurrentPrincipal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {
    private final StudentTestService tests;
    public StudentController(StudentTestService tests) { this.tests = tests; }
    @GetMapping("/tests")
    public ResponseEntity<List<StudentTestDto>> tests() {
        return ResponseEntity.ok(tests.tests(CurrentPrincipal.require()));
    }
}
