package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.learnium.model.user.Batch;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * M6 task 30 over HTTP, including Checkpoint B: a snapshot never changes when
 * a later marking lands — only an explicit regeneration refreshes it.
 */
@Transactional
class StudentReportFlowTest extends PublishingFixtures {
    @Autowired com.learnium.repository.exam.ExamAttemptRepository attempts;
    @Autowired com.learnium.repository.exam.QuestionAttemptRepository answers;

    private ExamInstance instance;
    private ExamInstanceQuestion algebraItem;
    private final Instant base = Instant.parse("2026-11-03T10:00:00Z");
    private int attemptNo;

    @BeforeEach
    void reportSetup() throws Exception {
        JsonNode definition = create();
        instance = instances
                .findById(UUID.fromString(publish(definition.path("id").asText(), publishBody()).path("id").asText()))
                .orElseThrow();
        Topic algebra = new Topic();
        algebra.setGradeSubject(pairing);
        algebra.setTopicName("Algebra");
        algebra = topics.saveAndFlush(algebra);
        algebraItem = writtenItem(instance, algebra, 10, 21);
        answer(attempt(100), algebraItem, new BigDecimal("4.00"));
    }

    private String url() {
        return "/api/v1/students/" + student.getUserId() + "/report";
    }

    @Test
    void generateReadAndRegenerateWithFrozenFacts() throws Exception {
        JsonNode created = json(mvc.perform(post(url()).with(as(owner))
                        .contentType("application/json")
                        .content(body(Map.of("teacherComment", "Keep practising."))))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(created.path("facts").path("studentName").asText()).isEqualTo(student.getDisplayName());
        assertThat(created.path("facts").path("batchName").isNull()).isTrue();
        assertThat(created.path("facts").path("overall").path("percentage").decimalValue())
                .isEqualByComparingTo("40.00");
        assertThat(created.path("teacherComment").asText()).isEqualTo("Keep practising.");
        String firstId = created.path("reportId").asText();

        JsonNode read = json(mvc.perform(get(url()).with(as(owner))).andExpect(status().isOk()).andReturn());
        assertThat(read.path("reportId").asText()).isEqualTo(firstId);

        // A new confirmed marking lands after generation: the snapshot must not move.
        answer(attempt(200), algebraItem, new BigDecimal("10.00"));
        JsonNode stillFrozen = json(mvc.perform(get(url()).with(as(owner))).andExpect(status().isOk()).andReturn());
        assertThat(stillFrozen.path("facts").path("overall").path("percentage").decimalValue())
                .isEqualByComparingTo("40.00");

        JsonNode regenerated = json(mvc.perform(post(url()).with(as(admin))
                        .contentType("application/json")
                        .content(body(Map.of("teacherComment", "Much better now."))))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(regenerated.path("reportId").asText()).isNotEqualTo(firstId);
        assertThat(regenerated.path("facts").path("overall").path("percentage").decimalValue())
                .isEqualByComparingTo("70.00");
        assertThat(regenerated.path("teacherComment").asText()).isEqualTo("Much better now.");
    }

    @Test
    void batchScopedReportIsSeparateFromTheAllBatchesOne() throws Exception {
        JsonNode scoped = json(mvc.perform(post(url()).with(as(owner))
                        .contentType("application/json")
                        .content(body(Map.of("batchId", batch.getBatchId().toString()))))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(scoped.path("facts").path("batchName").asText()).isEqualTo("Batch 8");
        assertThat(scoped.path("batchId").asText()).isEqualTo(batch.getBatchId().toString());

        mvc.perform(get(url()).with(as(owner))).andExpect(status().isNotFound());
        JsonNode read = json(mvc.perform(get(url() + "?batchId=" + batch.getBatchId()).with(as(owner)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(read.path("reportId").asText()).isEqualTo(scoped.path("reportId").asText());
    }

    @Test
    void scopeMatrixHidesUnmanagedAndForeignStudents() throws Exception {
        mvc.perform(post(url()).with(as(colleague)).contentType("application/json").content(body(Map.of())))
                .andExpect(status().isNotFound());
        mvc.perform(get(url()).with(as(colleague))).andExpect(status().isNotFound());
        mvc.perform(post(url()).with(as(student)).contentType("application/json").content(body(Map.of())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/students/" + foreign.getUserId() + "/report").with(as(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/students/" + owner.getUserId() + "/report").with(as(admin)))
                .andExpect(status().isNotFound());
        mvc.perform(get(url()).with(as(admin))).andExpect(status().isNotFound());
    }

    @Test
    void adminCanGenerateInBatchScope() throws Exception {
        mvc.perform(post(url()).with(as(admin)).contentType("application/json")
                        .content(body(Map.of("batchId", batch.getBatchId().toString()))))
                .andExpect(status().isCreated());
    }

    @Test
    void batchScopeDenialsAreHidden() throws Exception {
        // A batch id that does not exist in the caller's centre.
        mvc.perform(post(url()).with(as(owner)).contentType("application/json")
                        .content(body(Map.of("batchId", UUID.randomUUID().toString()))))
                .andExpect(status().isNotFound());
        // The real batch, but the colleague does not teach it.
        mvc.perform(post(url()).with(as(colleague)).contentType("application/json")
                        .content(body(Map.of("batchId", batch.getBatchId().toString()))))
                .andExpect(status().isNotFound());
        // A real batch the caller teaches, but the student is not a member of it.
        Batch other = new Batch();
        other.setCentre(centre);
        other.setName("Batch 9");
        other.setGradeSubject(pairing);
        other = batches.saveAndFlush(other);
        member(other, owner);
        mvc.perform(post(url()).with(as(owner)).contentType("application/json")
                        .content(body(Map.of("batchId", other.getBatchId().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void commentLongerThanTheLimitIsRejected() throws Exception {
        mvc.perform(post(url()).with(as(owner))
                        .contentType("application/json")
                        .content(body(Map.of("teacherComment", "x".repeat(2001)))))
                .andExpect(status().isBadRequest());
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

    private ExamAttempt attempt(int seconds) {
        ExamAttempt row = new ExamAttempt();
        row.setCentreId(centre.getCentreId());
        row.setExamInstance(instance);
        row.setUser(student);
        row.setAttemptNo(++attemptNo);
        row.setStartedAt(base.plusSeconds(seconds).minusSeconds(60));
        row.setDeadlineAt(base.plusSeconds(seconds));
        row.setSubmittedAt(base.plusSeconds(seconds));
        row.setStatus(ExamAttemptStatus.SUBMITTED);
        return attempts.saveAndFlush(row);
    }

    private void answer(ExamAttempt attempt, ExamInstanceQuestion item, BigDecimal score) {
        QuestionAttempt row = new QuestionAttempt();
        row.setCentreId(centre.getCentreId());
        row.setAttempt(attempt);
        row.setExamInstanceQuestion(item);
        row.setQuestion(item.getQuestion());
        row.setQuestionVersion(item.getQuestionVersion());
        row.setAnswerPayload(JSON.createObjectNode().put("text", "answer"));
        row.setMarkingStatus(MarkingStatus.CONFIRMED);
        row.setConfirmedScore(score);
        answers.saveAndFlush(row);
    }
}
