package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.model.content.Question;
import com.learnium.model.content.QuestionStatus;
import com.learnium.model.content.Topic;
import com.learnium.model.exam.ExamAttempt;
import com.learnium.model.exam.ExamAttemptStatus;
import com.learnium.model.exam.ExamInstance;
import com.learnium.model.exam.ExamInstanceQuestion;
import com.learnium.model.exam.MarkingStatus;
import com.learnium.model.exam.QuestionAttempt;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** M6 task 31 over HTTP: staff download in scope, the student's own download, and the hiding rules. */
@Transactional
class StudentReportPdfFlowTest extends PublishingFixtures {
    @Autowired com.learnium.repository.exam.ExamAttemptRepository attempts;
    @Autowired com.learnium.repository.exam.QuestionAttemptRepository answers;

    private final Instant base = Instant.parse("2026-11-03T10:00:00Z");

    @BeforeEach
    void reportSetup() throws Exception {
        JsonNode definition = create();
        ExamInstance instance = instances
                .findById(UUID.fromString(publish(definition.path("id").asText(), publishBody()).path("id").asText()))
                .orElseThrow();
        Topic algebra = new Topic();
        algebra.setGradeSubject(pairing);
        algebra.setTopicName("Algebra");
        algebra = topics.saveAndFlush(algebra);
        ExamInstanceQuestion item = writtenItem(instance, algebra, 10, 21);
        ExamAttempt attempt = new ExamAttempt();
        attempt.setCentreId(centre.getCentreId());
        attempt.setExamInstance(instance);
        attempt.setUser(student);
        attempt.setAttemptNo(1);
        attempt.setStartedAt(base.minusSeconds(60));
        attempt.setDeadlineAt(base);
        attempt.setSubmittedAt(base);
        attempt.setStatus(ExamAttemptStatus.SUBMITTED);
        attempt = attempts.saveAndFlush(attempt);
        QuestionAttempt row = new QuestionAttempt();
        row.setCentreId(centre.getCentreId());
        row.setAttempt(attempt);
        row.setExamInstanceQuestion(item);
        row.setQuestion(item.getQuestion());
        row.setQuestionVersion(item.getQuestionVersion());
        row.setAnswerPayload(JSON.createObjectNode().put("text", "answer"));
        row.setMarkingStatus(MarkingStatus.CONFIRMED);
        row.setConfirmedScore(new BigDecimal("4.00"));
        answers.saveAndFlush(row);

        mvc.perform(post("/api/v1/students/" + student.getUserId() + "/report").with(as(owner))
                        .contentType("application/json")
                        .content(body(Map.of("teacherComment", "Keep practising."))))
                .andExpect(status().isCreated());
    }

    @Test
    void staffDownloadRendersTheSnapshot() throws Exception {
        byte[] pdf = mvc.perform(get("/api/v1/students/" + student.getUserId() + "/report.pdf").with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string(
                        "Content-Disposition", "attachment; filename=\"report-" + student.getUsername() + ".pdf\""))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, StandardCharsets.ISO_8859_1)).startsWith("%PDF");
        try (var doc = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Student Progress Report").contains("40.0%").contains("Keep practising.");
        }
    }

    @Test
    void studentDownloadsTheirOwnLatestReport() throws Exception {
        mvc.perform(get("/api/v1/student/report.pdf").with(as(student)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    @Test
    void downloadsAreScopedAndHidden() throws Exception {
        UserAccount classmate = user(centre, UserRole.STUDENT);
        mvc.perform(get("/api/v1/student/report.pdf").with(as(classmate))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/student/report.pdf").with(as(owner))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/students/" + student.getUserId() + "/report.pdf").with(as(student)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/students/" + student.getUserId() + "/report.pdf").with(as(colleague)))
                .andExpect(status().isNotFound());
    }

    private ExamInstanceQuestion writtenItem(ExamInstance instance, Topic itemTopic, int marks, int order) throws Exception {
        Question q = new Question();
        q.setCentre(centre);
        q.setTopic(itemTopic);
        q.setQuestionType(written.getQuestionType());
        q.setPayload(JSON.readTree("{\"stem\":\"Explain\",\"modelAnswer\":\"Model\",\"rubric\":["
                + "{\"id\":\"a\",\"criterion\":\"A\",\"maxScore\":1},{\"id\":\"b\",\"criterion\":\"B\",\"maxScore\":1},"
                + "{\"id\":\"c\",\"criterion\":\"C\",\"maxScore\":1}],\"maxScore\":3}"));
        q.setCorrectRef("modelAnswer");
        q.setStatus(QuestionStatus.APPROVED);
        q = questions.saveAndFlush(q);
        ExamInstanceQuestion item = new ExamInstanceQuestion();
        item.setCentreId(centre.getCentreId());
        item.setExamInstance(instance);
        item.setQuestion(q);
        item.setQuestionVersion(q.getVersion());
        item.setQuestionOrder(order);
        item.setMarks(BigDecimal.valueOf(marks));
        item.setPayloadSnapshot(q.getPayload().deepCopy());
        item.setCorrectRefSnapshot(q.getCorrectRef());
        return frozen.saveAndFlush(item);
    }
}
