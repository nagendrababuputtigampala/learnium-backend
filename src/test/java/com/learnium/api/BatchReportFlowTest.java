package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/** M6 task 29 endpoint: batch topic report over HTTP with the full scope matrix. */
@Transactional
class BatchReportFlowTest extends PublishingFixtures {
    @Autowired com.learnium.repository.exam.ExamAttemptRepository attempts;
    @Autowired com.learnium.repository.exam.QuestionAttemptRepository answers;

    private ExamInstance instance;
    private final Instant base = Instant.parse("2026-11-03T10:00:00Z");

    @BeforeEach
    void reportSetup() throws Exception {
        JsonNode definition = create();
        instance = instances.findById(UUID.fromString(publish(definition.path("id").asText(), publishBody()).path("id").asText()))
                .orElseThrow();
        Topic algebra = new Topic();
        algebra.setGradeSubject(pairing);
        algebra.setTopicName("Algebra");
        algebra = topics.saveAndFlush(algebra);
        ExamInstanceQuestion item = writtenItem(instance, algebra, 10, 21);
        ExamAttempt attempt = attempt(100, ExamAttemptStatus.SUBMITTED);
        answer(attempt, item, MarkingStatus.CONFIRMED, new BigDecimal("4.00"));
    }

    @Test
    void managingTeacherAndAdminReadTheReport() throws Exception {
        for (var caller : new com.learnium.model.user.UserAccount[] {owner, admin}) {
            JsonNode response = json(mvc.perform(get("/api/v1/batches/" + batch.getBatchId() + "/topic-report").with(as(caller)))
                    .andExpect(status().isOk())
                    .andReturn());
            assertThat(response.path("batchName").asText()).isEqualTo("Batch 8");
            JsonNode algebra = response.path("topics").get(0);
            assertThat(algebra.path("topicName").asText()).isEqualTo("Algebra");
            assertThat(algebra.path("percentage").decimalValue()).isEqualByComparingTo("40.00");
            assertThat(algebra.path("studentCount").asInt()).isEqualTo(1);
            assertThat(algebra.path("confirmedItems").asInt()).isEqualTo(1);
        }
    }

    @Test
    void unmanagedForeignAndUnknownScopesAreHiddenAndStudentsAreForbidden() throws Exception {
        String url = "/api/v1/batches/" + batch.getBatchId() + "/topic-report";
        mvc.perform(get(url).with(as(colleague))).andExpect(status().isNotFound());
        mvc.perform(get(url).with(as(foreign))).andExpect(status().isNotFound());
        mvc.perform(get(url).with(as(student))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/batches/" + UUID.randomUUID() + "/topic-report").with(as(admin)))
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

    private ExamAttempt attempt(int seconds, ExamAttemptStatus status) {
        ExamAttempt row = new ExamAttempt();
        row.setCentreId(centre.getCentreId());
        row.setExamInstance(instance);
        row.setUser(student);
        row.setAttemptNo(1);
        row.setStartedAt(base.plusSeconds(seconds).minusSeconds(60));
        row.setDeadlineAt(base.plusSeconds(seconds));
        row.setSubmittedAt(base.plusSeconds(seconds));
        row.setStatus(status);
        return attempts.saveAndFlush(row);
    }

    private void answer(ExamAttempt attempt, ExamInstanceQuestion item, MarkingStatus status, BigDecimal score) {
        QuestionAttempt row = new QuestionAttempt();
        row.setCentreId(centre.getCentreId());
        row.setAttempt(attempt);
        row.setExamInstanceQuestion(item);
        row.setQuestion(item.getQuestion());
        row.setQuestionVersion(item.getQuestionVersion());
        row.setAnswerPayload(JSON.createObjectNode().put("text", "answer"));
        row.setMarkingStatus(status);
        row.setConfirmedScore(score);
        answers.saveAndFlush(row);
    }
}
