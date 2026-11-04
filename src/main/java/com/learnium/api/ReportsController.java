package com.learnium.api;

import com.learnium.api.dto.ReportDtos.BatchTopicReportDto;
import com.learnium.api.dto.ReportDtos.GenerateReportRequest;
import com.learnium.api.dto.ReportDtos.StudentReportDto;
import com.learnium.report.BatchReportService;
import com.learnium.report.StudentReportService;
import com.learnium.security.CurrentPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** M6 report endpoints: batch analytics (S2) and student report snapshots (S3). */
@RestController
public class ReportsController {

    private final BatchReportService batchReports;
    private final StudentReportService studentReports;

    public ReportsController(BatchReportService batchReports, StudentReportService studentReports) {
        this.batchReports = batchReports;
        this.studentReports = studentReports;
    }

    @GetMapping("/api/v1/batches/{batchId}/topic-report")
    @PreAuthorize("hasAnyRole('CENTRE_ADMIN', 'TEACHER')")
    public ResponseEntity<BatchTopicReportDto> batchTopicReport(@PathVariable UUID batchId) {
        return ResponseEntity.ok(batchReports.topicReport(CurrentPrincipal.require(), batchId));
    }

    @PostMapping("/api/v1/students/{userId}/report")
    @PreAuthorize("hasAnyRole('CENTRE_ADMIN', 'TEACHER')")
    public ResponseEntity<StudentReportDto> generateStudentReport(
            @PathVariable UUID userId, @Valid @RequestBody GenerateReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(studentReports.generate(CurrentPrincipal.require(), userId, request));
    }

    @GetMapping("/api/v1/students/{userId}/report")
    @PreAuthorize("hasAnyRole('CENTRE_ADMIN', 'TEACHER')")
    public ResponseEntity<StudentReportDto> studentReport(
            @PathVariable UUID userId, @RequestParam(required = false) UUID batchId) {
        return ResponseEntity.ok(studentReports.latest(CurrentPrincipal.require(), userId, batchId));
    }
}
