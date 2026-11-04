package com.learnium.report;

import com.learnium.api.dto.ReportDtos.StoredReportFacts;
import com.learnium.api.dto.ReportDtos.StudentReportDto;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

/**
 * Renders a frozen report snapshot as a branded one-document PDF (M6 task 31).
 * Built directly on PDFBox (already the extraction dependency) so no second
 * PDF stack joins the classpath. Everything drawn comes from the snapshot —
 * never from live facts.
 */
@Component
public class ReportPdfRenderer {

    private static final PDType1Font BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDType1Font REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private static final PDType1Font ITALIC = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);
    private static final float MARGIN = 48f;
    private static final float PAGE_TOP = PDRectangle.A4.getHeight() - MARGIN;
    private static final float PAGE_BOTTOM = 54f;
    private static final float CONTENT_WIDTH = PDRectangle.A4.getWidth() - 2 * MARGIN;
    private static final Color INDIGO = new Color(67, 56, 202);
    private static final Color MARIGOLD = new Color(245, 158, 11);
    private static final Color INK = new Color(28, 25, 23);
    private static final Color MUTED = new Color(110, 105, 100);
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("MMM d, yyyy, h:mm a");

    @FunctionalInterface
    interface IoSupplier<T> {
        T get() throws IOException;
    }

    /** Runs a PDFBox operation, surfacing I/O failure as an unchecked rendering failure. */
    static <T> T io(IoSupplier<T> supplier) {
        try {
            return supplier.get();
        } catch (IOException e) {
            throw new IllegalStateException("Report PDF could not be rendered", e);
        }
    }

    public byte[] render(StudentReportDto report, String centreName, String centreTimezone) {
        return io(() -> {
            try (PDDocument doc = new PDDocument();
                    ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Cursor cursor = new Cursor(doc);
                draw(cursor, report, centreName, centreTimezone);
                cursor.closeStream();
                doc.save(out);
                return out.toByteArray();
            }
        });
    }

    private void draw(Cursor c, StudentReportDto report, String centreName, String centreTimezone)
            throws IOException {
        StoredReportFacts facts = report.facts();
        c.text(centreName, BOLD, 13, INDIGO);
        c.gap(4);
        c.text("Student Progress Report", BOLD, 22, INK);
        c.gap(6);
        c.rule(MARIGOLD);
        c.gap(16);

        c.text(facts.studentName(), BOLD, 14, INK);
        c.gap(3);
        StringBuilder meta = new StringBuilder();
        if (facts.username() != null) {
            meta.append(facts.username());
        }
        if (facts.batchName() != null) {
            if (!meta.isEmpty()) {
                meta.append(" · ");
            }
            meta.append(facts.batchName());
        }
        if (!meta.isEmpty()) {
            c.text(meta.toString(), REGULAR, 10, MUTED);
            c.gap(2);
        }
        String generated = WHEN.format(report.generatedAt().atZone(ZoneId.of(centreTimezone)));
        c.text("Generated " + generated, REGULAR, 10, MUTED);
        c.gap(16);

        var overall = facts.overall();
        c.text("Overall score", BOLD, 12, INDIGO);
        c.gap(4);
        c.text(
                String.format(
                        "%.1f%%  (%s of %s marks)",
                        overall.percentage(),
                        overall.earnedMarks().toPlainString(),
                        overall.availableMarks().toPlainString()),
                BOLD,
                16,
                INK);
        c.gap(4);
        String coverage = overall.confirmedItems() + " confirmed answers"
                + (overall.pendingItems() > 0
                        ? " · " + overall.pendingItems() + " awaiting marking (not counted yet)"
                        : "");
        c.text(coverage, REGULAR, 10, MUTED);
        c.gap(14);

        if (report.teacherComment() != null && !report.teacherComment().isBlank()) {
            c.text("Teacher comment", BOLD, 12, INDIGO);
            c.gap(4);
            c.wrapped(report.teacherComment(), ITALIC, 11, INK);
            c.gap(14);
        }

        bandLine(c, facts, "STRONG", "Strong areas");
        bandLine(c, facts, "FOCUS", "Areas to improve");

        c.text("Topics", BOLD, 12, INDIGO);
        c.gap(6);
        if (facts.topics().isEmpty()) {
            c.text("No confirmed topic results yet.", REGULAR, 11, MUTED);
            c.gap(12);
        } else {
            for (var topic : facts.topics()) {
                c.ensure(16);
                c.textAt(MARGIN, fit(topic.topicName(), BOLD, 11, 250), BOLD, 11, INK);
                c.textAt(320, String.format("%.1f%%", topic.percentage()), BOLD, 11, INK);
                c.textAt(
                        385,
                        topic.band() == null ? "Insufficient data" : bandLabel(topic.band()),
                        REGULAR,
                        11,
                        MUTED);
                c.textAt(500, topic.confirmedItems() + " confirmed", REGULAR, 11, MUTED);
                c.y -= 16;
            }
            c.gap(8);
        }

        c.text("Test history", BOLD, 12, INDIGO);
        c.gap(6);
        if (facts.tests().isEmpty()) {
            c.text("No completed tests yet.", REGULAR, 11, MUTED);
            c.gap(12);
        } else {
            DateTimeFormatter day = DateTimeFormatter.ofPattern("MMM d, yyyy");
            ZoneId zone = ZoneId.of(centreTimezone);
            for (var test : facts.tests()) {
                String line = day.format(test.submittedAt().atZone(zone))
                        + " — " + test.testName() + " (" + test.batchName() + ") — "
                        + String.format("%.1f%%", test.percentage())
                        + (test.pendingItems() > 0 ? " · marking pending" : "");
                c.wrapped(line, REGULAR, 11, INK);
                c.gap(2);
            }
            c.gap(10);
        }

        c.rule(MARIGOLD);
        c.gap(8);
        c.text(
                "Percentages use confirmed results only. Generated by Learnium for "
                        + centreName + ".",
                REGULAR,
                9,
                MUTED);
    }

    private void bandLine(Cursor c, StoredReportFacts facts, String band, String heading)
            throws IOException {
        List<String> names = facts.topics().stream()
                .filter(topic -> band.equals(topic.band()))
                .map(topic -> topic.topicName() + " (" + String.format("%.1f%%", topic.percentage()) + ")")
                .toList();
        if (names.isEmpty()) {
            return;
        }
        c.text(heading, BOLD, 12, INDIGO);
        c.gap(4);
        c.wrapped(String.join(", ", names), REGULAR, 11, INK);
        c.gap(14);
    }

    private static String bandLabel(String band) {
        return switch (band) {
            case "STRONG" -> "Strong";
            case "DEVELOPING" -> "Developing";
            default -> "Focus";
        };
    }

    /** Trims a string to a measured width, ellipsizing the tail. */
    static String fit(String text, PDFont font, float size, float maxWidth) throws IOException {
        String value = safe(text);
        if (width(font, size, value) <= maxWidth) {
            return value;
        }
        while (!value.isEmpty() && width(font, size, value + "…") > maxWidth) {
            value = value.substring(0, value.length() - 1);
        }
        return value + "…";
    }

    /** Keeps only characters the base fonts can encode; anything else becomes '?'. */
    static String safe(String text) throws IOException {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            try {
                REGULAR.encode(String.valueOf(ch));
                out.append(ch);
            } catch (IllegalArgumentException e) {
                out.append('?');
            }
        }
        return out.toString();
    }

    private static float width(PDFont font, float size, String text) throws IOException {
        return font.getStringWidth(text) / 1000f * size;
    }

    /** Word-wraps a string to the content width at the given font/size. */
    private static List<String> wrap(String text, PDFont font, float size) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : safe(text).split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (width(font, size, candidate) <= CONTENT_WIDTH) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        lines.add(line.toString());
        return lines;
    }

    /** Page cursor: owns the current page + content stream and flows text downward. */
    private static final class Cursor {
        private final PDDocument doc;
        private PDPageContentStream stream;
        private float y;

        Cursor(PDDocument doc) throws IOException {
            this.doc = doc;
            openPage();
        }

        private void openPage() throws IOException {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            stream = new PDPageContentStream(doc, page);
            y = PAGE_TOP;
        }

        void closeStream() throws IOException {
            stream.close();
        }

        void ensure(float space) throws IOException {
            if (y - space < PAGE_BOTTOM) {
                stream.close();
                openPage();
            }
        }

        void gap(float points) {
            y -= points;
        }

        void text(String value, PDFont font, float size, Color color) throws IOException {
            ensure(size + 2);
            textAt(MARGIN, value, font, size, color);
            y -= size + 2;
        }

        void textAt(float x, String value, PDFont font, float size, Color color) throws IOException {
            stream.beginText();
            stream.setFont(font, size);
            stream.setNonStrokingColor(color);
            stream.newLineAtOffset(x, y);
            stream.showText(safe(value));
            stream.endText();
        }

        void wrapped(String value, PDFont font, float size, Color color) throws IOException {
            for (String line : wrap(value, font, size)) {
                text(line, font, size, color);
                y -= 2;
            }
        }

        void rule(Color color) throws IOException {
            ensure(4);
            stream.setNonStrokingColor(color);
            stream.addRect(MARGIN, y, CONTENT_WIDTH, 2);
            stream.fill();
            y -= 2;
        }
    }
}
