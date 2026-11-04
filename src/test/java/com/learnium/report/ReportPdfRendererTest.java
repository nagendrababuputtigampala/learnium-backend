package com.learnium.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.learnium.api.dto.ReportDtos.ReportOverallDto;
import com.learnium.api.dto.ReportDtos.ReportTestDto;
import com.learnium.api.dto.ReportDtos.ReportTopicDto;
import com.learnium.api.dto.ReportDtos.StoredReportFacts;
import com.learnium.api.dto.ReportDtos.StudentReportDto;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

/** The PDF renders only snapshot content, with branded sections, safe text and page flow. */
class ReportPdfRendererTest {
    private final ReportPdfRenderer renderer = new ReportPdfRenderer();

    private StudentReportDto report(
            String studentName, String username, String batchName, String comment,
            List<ReportTopicDto> topics, List<ReportTestDto> tests, ReportOverallDto overall) {
        return new StudentReportDto(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                comment,
                UUID.randomUUID(),
                Instant.parse("2026-11-03T18:30:00Z"),
                new StoredReportFacts(studentName, username, batchName, overall, topics, tests));
    }

    private ReportOverallDto overall(long confirmed, long pending, String earned, String available, String percentage) {
        return new ReportOverallDto(
                confirmed, pending, new BigDecimal(earned), new BigDecimal(available),
                new BigDecimal(percentage), new BigDecimal("90.00"));
    }

    private ReportTopicDto topic(String name, String percentage, String band, long confirmed) {
        return new ReportTopicDto(
                UUID.randomUUID(), name, confirmed, 0, new BigDecimal("10"), new BigDecimal("20"),
                new BigDecimal(percentage), band);
    }

    private String textOf(byte[] pdf) throws IOException {
        try (var doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void rendersABrandedSnapshotDocument() throws Exception {
        var report = report(
                "Simran Kaur", "simran.k", "Batch 8",
                "Steady progress this month. Fractions are now secure and the next step is word problems with two steps.",
                List.of(
                        topic("Fractions", "90.0", "STRONG", 5),
                        topic("Algebra", "65.0", "DEVELOPING", 4),
                        topic("Geometry", "42.5", "FOCUS", 4),
                        topic("A very long topic name that will not fit the column width at all", "100.0", null, 1)),
                List.of(new ReportTestDto(
                        UUID.randomUUID(), "Fractions Quiz", "Batch 8",
                        Instant.parse("2026-11-01T17:00:00Z"),
                        new BigDecimal("40"), new BigDecimal("50"), new BigDecimal("80.0"), 1)),
                overall(9, 1, "62", "90", "68.89"));

        byte[] pdf = renderer.render(report, "Demo Tutoring Centre", "America/Vancouver");

        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).startsWith("%PDF");
        String text = textOf(pdf);
        assertThat(text)
                .contains("Demo Tutoring Centre")
                .contains("Student Progress Report")
                .contains("Simran Kaur")
                .contains("simran.k")
                .contains("68.9%")
                .contains("9 confirmed answers")
                .contains("1 awaiting marking")
                .contains("Teacher comment")
                .contains("Steady progress this month.")
                .contains("Strong areas")
                .contains("Areas to improve")
                .contains("Fractions (90.0%)")
                .contains("Algebra 65.0% Developing")
                .contains("Insufficient data")
                .contains("Fractions Quiz")
                .contains("marking pending")
                .contains("Percentages use confirmed results only.");
    }

    @Test
    void rendersAnEmptySnapshotAndSanitizesUnencodableCharacters() throws Exception {
        var report = report("Renée 😀 Kaur", null, null, null, List.of(), List.of(), overall(0, 0, "0", "0", "0"));

        byte[] pdf = renderer.render(report, "Centre", "UTC");

        String text = textOf(pdf);
        assertThat(text)
                .contains("Renée ?? Kaur")
                .contains("No confirmed topic results yet.")
                .contains("No completed tests yet.")
                .doesNotContain("Teacher comment");
    }

    @Test
    void flowsOntoAdditionalPagesForLongReports() throws Exception {
        List<ReportTopicDto> topics = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            topics.add(topic("Topic " + i, "50.0", "DEVELOPING", 3));
        }
        var report = report("Simran Kaur", "simran.k", null, null, topics, List.of(), overall(240, 0, "1", "2", "50"));

        byte[] pdf = renderer.render(report, "Centre", "UTC");

        try (var doc = Loader.loadPDF(pdf)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
        }
    }

    @Test
    void ioFailureIsWrapped() {
        assertThatThrownBy(() -> ReportPdfRenderer.io(() -> {
                    throw new IOException("disk gone");
                }))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void metaLineHasNoSeparatorWhenOnlyTheBatchIsKnown() throws Exception {
        var report = report(
                "Simran Kaur", null, "Batch 8", null, List.of(), List.of(), overall(0, 0, "0", "0", "0"));

        String text = textOf(renderer.render(report, "Centre", "UTC"));

        assertThat(text).contains("Batch 8").doesNotContain("· Batch 8");
    }

    @Test
    void blankCommentIsOmitted() throws Exception {
        var report = report(
                "Simran Kaur", "simran.k", null, "   ", List.of(), List.of(), overall(0, 0, "0", "0", "0"));

        String text = textOf(renderer.render(report, "Centre", "UTC"));

        assertThat(text).doesNotContain("Teacher comment");
    }

    @Test
    void fitKeepsShortTextTrimsLongTextAndDrainsAtTinyWidths() throws Exception {
        var bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        assertThat(ReportPdfRenderer.fit("Fractions", bold, 11, 250)).isEqualTo("Fractions");
        assertThat(ReportPdfRenderer.fit(
                        "A very long topic name that will not fit the column width at all", bold, 11, 250))
                .endsWith("…")
                .hasSizeLessThan(60);
        assertThat(ReportPdfRenderer.fit("Hello", bold, 11, 1)).isEqualTo("…");
    }

    @Test
    void safeKeepsWinAnsiAndReplacesTheRest() throws Exception {
        assertThat(ReportPdfRenderer.safe("café — 50%")).isEqualTo("café — 50%");
        assertThat(ReportPdfRenderer.safe("a😀b")).isEqualTo("a??b");
    }
}
