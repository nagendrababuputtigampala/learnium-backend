package com.learnium.report;

import com.learnium.api.dto.ReportDtos.StudentReportDto;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.user.Centre;
import com.learnium.repository.user.CentreRepository;
import com.learnium.security.LearniumPrincipal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PDF downloads of report snapshots (M6 task 31): staff in scope, or the student themself. */
@Service
public class ReportPdfService {

    public record PdfFile(String filename, byte[] bytes) {}

    private final StudentReportService reports;
    private final CentreRepository centres;
    private final ReportPdfRenderer renderer;

    public ReportPdfService(StudentReportService reports, CentreRepository centres, ReportPdfRenderer renderer) {
        this.reports = reports;
        this.centres = centres;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public PdfFile staffPdf(LearniumPrincipal caller, UUID studentId, UUID batchId) {
        return render(caller.centreId(), reports.latest(caller, studentId, batchId));
    }

    @Transactional(readOnly = true)
    public PdfFile studentPdf(LearniumPrincipal student) {
        return render(student.centreId(), reports.latestForStudent(student));
    }

    private PdfFile render(UUID centreId, StudentReportDto report) {
        Centre centre = centres.findById(centreId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SYS_NOT_FOUND));
        byte[] bytes = renderer.render(report, centre.getName(), centre.getTimezone());
        return new PdfFile(filename(report), bytes);
    }

    /** report-<username>.pdf, with the identifier reduced to filename-safe characters. */
    private static String filename(StudentReportDto report) {
        String base = report.facts().username();
        if (base == null || base.isBlank()) {
            base = report.studentId().toString();
        }
        return "report-" + base.replaceAll("[^A-Za-z0-9._-]", "-") + ".pdf";
    }
}
