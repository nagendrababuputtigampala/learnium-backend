package com.learnium.api;

import com.learnium.api.dto.ReportDtos.BatchTopicReportDto;
import com.learnium.report.BatchReportService;
import com.learnium.security.CurrentPrincipal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** M6 report endpoints: batch analytics first; student reports join in S3. */
@RestController
public class ReportsController {

    private final BatchReportService batchReports;

    public ReportsController(BatchReportService batchReports) {
        this.batchReports = batchReports;
    }

    @GetMapping("/api/v1/batches/{batchId}/topic-report")
    @PreAuthorize("hasAnyRole('CENTRE_ADMIN', 'TEACHER')")
    public ResponseEntity<BatchTopicReportDto> batchTopicReport(@PathVariable UUID batchId) {
        return ResponseEntity.ok(batchReports.topicReport(CurrentPrincipal.require(), batchId));
    }
}
