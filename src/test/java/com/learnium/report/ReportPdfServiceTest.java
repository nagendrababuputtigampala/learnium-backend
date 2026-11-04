package com.learnium.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.learnium.api.dto.ReportDtos.ReportOverallDto;
import com.learnium.api.dto.ReportDtos.StoredReportFacts;
import com.learnium.api.dto.ReportDtos.StudentReportDto;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserRole;
import com.learnium.repository.user.CentreRepository;
import com.learnium.security.LearniumPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Filename rules and centre lookup of the PDF download service. */
class ReportPdfServiceTest {
    private final StudentReportService reports = mock(StudentReportService.class);
    private final CentreRepository centres = mock(CentreRepository.class);
    private final ReportPdfRenderer renderer = mock(ReportPdfRenderer.class);
    private final ReportPdfService service = new ReportPdfService(reports, centres, renderer);
    private final UUID centreId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    private StudentReportDto reportWithUsername(String username) {
        return new StudentReportDto(
                UUID.randomUUID(),
                studentId,
                null,
                null,
                UUID.randomUUID(),
                Instant.EPOCH,
                new StoredReportFacts(
                        "Student",
                        username,
                        null,
                        new ReportOverallDto(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                        List.of(),
                        List.of()));
    }

    private Centre centre() {
        Centre centre = new Centre();
        centre.setCentreId(centreId);
        centre.setName("Demo Centre");
        centre.setTimezone("America/Vancouver");
        return centre;
    }

    @Test
    void staffPdfUsesSanitizedUsernameFilename() {
        LearniumPrincipal admin = LearniumPrincipal.staff(UUID.randomUUID(), centreId, UserRole.CENTRE_ADMIN);
        var report = reportWithUsername("sim ran.k!");
        when(reports.latest(admin, studentId, null)).thenReturn(report);
        when(centres.findById(centreId)).thenReturn(Optional.of(centre()));
        when(renderer.render(report, "Demo Centre", "America/Vancouver")).thenReturn(new byte[] {1, 2, 3});

        var file = service.staffPdf(admin, studentId, null);

        assertThat(file.filename()).isEqualTo("report-sim-ran.k-.pdf");
        assertThat(file.bytes()).containsExactly(1, 2, 3);
    }

    @Test
    void studentPdfFallsBackToTheStudentIdWithoutAUsername() {
        LearniumPrincipal student = LearniumPrincipal.student(studentId, centreId);
        var report = reportWithUsername(null);
        when(reports.latestForStudent(student)).thenReturn(report);
        when(centres.findById(centreId)).thenReturn(Optional.of(centre()));
        when(renderer.render(report, "Demo Centre", "America/Vancouver")).thenReturn(new byte[] {9});

        var file = service.studentPdf(student);

        assertThat(file.filename()).isEqualTo("report-" + studentId + ".pdf");
    }

    @Test
    void missingCentreIsNotFound() {
        LearniumPrincipal student = LearniumPrincipal.student(studentId, centreId);
        when(reports.latestForStudent(student)).thenReturn(reportWithUsername("simran.k"));
        when(centres.findById(centreId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.studentPdf(student))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SYS_NOT_FOUND));
    }
}
