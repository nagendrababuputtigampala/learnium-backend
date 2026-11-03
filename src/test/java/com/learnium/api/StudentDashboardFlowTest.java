package com.learnium.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.model.content.*;
import com.learnium.model.exam.*;
import com.learnium.model.user.*;
import com.learnium.repository.exam.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class StudentDashboardFlowTest extends PublishingFixtures {
    @Autowired ExamAttemptRepository attempts;
    @Autowired QuestionAttemptRepository answers;
    private ExamInstance firstInstance;
    private ExamInstance secondInstance;
    private Batch secondBatch;
    private int firstAttemptNo;
    private int secondAttemptNo;
    private final Instant base = Instant.parse("2026-11-03T10:00:00Z");

    @BeforeEach void dashboardSetup() throws Exception {
        JsonNode definition = create();
        firstInstance = instance(publish(definition.path("id").asText(), publishBody()));
        secondBatch = new Batch(); secondBatch.setCentre(centre); secondBatch.setName("Batch 8B");
        secondBatch.setGradeSubject(pairing); secondBatch = batches.saveAndFlush(secondBatch); member(secondBatch, owner); member(secondBatch, student);
        Map<String, Object> secondPublish = publishBody(); secondPublish.put("batchId", secondBatch.getBatchId());
        secondInstance = instance(publish(definition.path("id").asText(), secondPublish));

        Topic strong = topic("Strong topic"), developing = topic("Developing topic"), focus = topic("Focus topic");
        ExamInstanceQuestion strongItem = writtenItem(firstInstance, strong, 10, 10);
        ExamInstanceQuestion developingItem = writtenItem(firstInstance, developing, 10, 11);
        ExamInstanceQuestion focusItem = writtenItem(firstInstance, focus, 10, 12);
        ExamInstanceQuestion firstObjective = frozen.frozenItem(centre.getCentreId(), firstInstance.getExamInstanceId(),
                frozen.frozen(centre.getCentreId(), firstInstance.getExamInstanceId()).stream()
                        .filter(item -> item.getQuestion().getQuestionId().equals(mcq.getQuestionId())).findFirst().orElseThrow()
                        .getExamInstanceQuestionId()).orElseThrow();
        ExamInstanceQuestion secondObjective = frozen.frozen(centre.getCentreId(), secondInstance.getExamInstanceId()).stream()
                .filter(item -> item.getQuestion().getQuestionId().equals(mcq.getQuestionId())).findFirst().orElseThrow();
        ExamInstanceQuestion pendingItem = frozen.frozen(centre.getCentreId(), secondInstance.getExamInstanceId()).stream()
                .filter(item -> item.getQuestion().getQuestionId().equals(written.getQuestionId())).findFirst().orElseThrow();

        for (int i = 1; i <= 3; i++) confirmed(student, firstInstance, strongItem, "8.00", i);
        for (int i = 4; i <= 6; i++) confirmed(student, firstInstance, developingItem, "6.00", i);
        for (int i = 7; i <= 9; i++) confirmed(student, firstInstance, focusItem, "5.00", i);
        objective(student, firstInstance, firstObjective, true, 10);
        ExamAttempt latest = objective(student, secondInstance, secondObjective, true, 11);
        pending(latest, pendingItem);

        for (int i = 0; i < 7; i++) {
            UserAccount peer = user(centre, UserRole.STUDENT); member(batch, peer);
            confirmed(peer, firstInstance, strongItem, String.valueOf(i + 1), 20 + i);
            if (i < 6) confirmed(peer, firstInstance, focusItem, "4.00", 30 + i);
        }
        UserAccount foreignFacts = user(centre, UserRole.STUDENT); member(batch, foreignFacts);
        confirmed(foreignFacts, firstInstance, developingItem, "10.00", 40);
        ExamAttempt invalid = attempt(student, firstInstance, base.plusSeconds(10_000), ExamAttemptStatus.INVALIDATED);
        answer(invalid, strongItem, MarkingStatus.CONFIRMED, false, new BigDecimal("10.00"));
    }

    @Test void dashboardUsesOnlyOwnedFinalFactsAndAppliesBandAndComparisonThresholds() throws Exception {
        JsonNode response = json(mvc.perform(get("/api/v1/student/dashboard").with(as(student)))
                .andExpect(status().isOk()).andReturn());
        assertTopic(response, "Strong topic", 3, "24.00", "30.00", "80.00", "STRONG");
        assertTopic(response, "Developing topic", 3, "18.00", "30.00", "60.00", "DEVELOPING");
        assertTopic(response, "Focus topic", 3, "15.00", "30.00", "50.00", "FOCUS");
        JsonNode insufficient = findTopic(response, "Fractions");
        assertThat(insufficient.path("confirmedItemCount").asLong()).isEqualTo(2);
        assertThat(insufficient.path("band").isNull()).isTrue();
        assertThat(response.path("pendingItemCount").asLong()).isEqualTo(1);
        assertThat(response.path("strongAreas").get(0).path("topicName").asText()).isEqualTo("Strong topic");
        assertThat(response.path("areasToImprove").get(0).path("topicName").asText()).isEqualTo("Focus topic");

        assertThat(response.path("comparisons")).hasSize(1);
        JsonNode comparison = response.path("comparisons").get(0);
        assertThat(comparison.path("topicName").asText()).isEqualTo("Strong topic");
        assertThat(comparison.path("studentCount").asInt()).isEqualTo(8);
        assertThat(comparison.path("batchAveragePercent").decimalValue()).isEqualByComparingTo("52.00");
        assertThat(comparison.path("percentile").decimalValue()).isEqualByComparingTo("100.00");

        assertThat(response.path("recentResults")).hasSize(5);
        assertThat(response.path("recentResults").get(0).path("batchName").asText()).isEqualTo("Batch 8B");
        assertThat(response.path("recentResults").get(0).path("markingPending").asBoolean()).isTrue();
        assertThat(response.path("recentResults").get(1).path("markingPending").asBoolean()).isFalse();
    }

    @Test void newStudentGetsAnEmptyDashboardAndStaffCannotReadIt() throws Exception {
        UserAccount newcomer = user(centre, UserRole.STUDENT);
        mvc.perform(get("/api/v1/student/dashboard").with(as(newcomer))).andExpect(status().isOk())
                .andExpect(content().json("{\"topics\":[],\"strongAreas\":[],\"areasToImprove\":[],"
                        + "\"recentResults\":[],\"comparisons\":[],\"pendingItemCount\":0}"));
        mvc.perform(get("/api/v1/student/dashboard").with(as(owner))).andExpect(status().isForbidden());
    }

    private ExamInstance instance(JsonNode published) {
        return instances.findById(UUID.fromString(published.path("id").asText())).orElseThrow();
    }
    private Topic topic(String name) {
        Topic value = new Topic(); value.setGradeSubject(pairing); value.setTopicName(name); return topics.saveAndFlush(value);
    }
    private ExamInstanceQuestion writtenItem(ExamInstance instance, Topic itemTopic, int marks, int order) throws Exception {
        Question q = new Question(); q.setCentre(centre); q.setTopic(itemTopic); q.setQuestionType(written.getQuestionType());
        q.setPayload(JSON.readTree("{\"stem\":\"Explain\",\"modelAnswer\":\"Model\",\"rubric\":["
                + "{\"id\":\"a\",\"criterion\":\"A\",\"maxScore\":1},{\"id\":\"b\",\"criterion\":\"B\",\"maxScore\":1},{\"id\":\"c\",\"criterion\":\"C\",\"maxScore\":1}],\"maxScore\":3}"));
        q.setCorrectRef("modelAnswer"); q.setStatus(QuestionStatus.APPROVED); q = questions.saveAndFlush(q);
        ExamInstanceQuestion item = new ExamInstanceQuestion(); item.setCentreId(centre.getCentreId()); item.setExamInstance(instance);
        item.setQuestion(q); item.setQuestionVersion(q.getVersion()); item.setQuestionOrder(order); item.setMarks(BigDecimal.valueOf(marks));
        item.setPayloadSnapshot(q.getPayload().deepCopy()); item.setCorrectRefSnapshot(q.getCorrectRef()); return frozen.saveAndFlush(item);
    }
    private void confirmed(UserAccount user, ExamInstance instance, ExamInstanceQuestion item, String score, int seconds) {
        ExamAttempt attempt = attempt(user, instance, base.plusSeconds(seconds), ExamAttemptStatus.SUBMITTED);
        answer(attempt, item, MarkingStatus.CONFIRMED, false, new BigDecimal(score));
    }
    private ExamAttempt objective(UserAccount user, ExamInstance instance, ExamInstanceQuestion item, boolean correct, int seconds) {
        ExamAttempt attempt = attempt(user, instance, base.plusSeconds(seconds), ExamAttemptStatus.AUTO_SUBMITTED);
        answer(attempt, item, MarkingStatus.AUTO, correct, null); return attempt;
    }
    private ExamAttempt attempt(UserAccount user, ExamInstance instance, Instant submitted, ExamAttemptStatus status) {
        ExamAttempt row = new ExamAttempt(); row.setCentreId(centre.getCentreId()); row.setExamInstance(instance); row.setUser(user);
        row.setAttemptNo(instance == firstInstance ? ++firstAttemptNo : ++secondAttemptNo); row.setStartedAt(submitted.minusSeconds(60));
        row.setDeadlineAt(submitted); row.setSubmittedAt(submitted); row.setStatus(status); return attempts.saveAndFlush(row);
    }
    private void pending(ExamAttempt attempt, ExamInstanceQuestion item) { answer(attempt, item, MarkingStatus.PENDING, false, null); }
    private void answer(ExamAttempt attempt, ExamInstanceQuestion item, MarkingStatus status, boolean correct, BigDecimal score) {
        QuestionAttempt row = new QuestionAttempt(); row.setCentreId(centre.getCentreId()); row.setAttempt(attempt);
        row.setExamInstanceQuestion(item); row.setQuestion(item.getQuestion()); row.setQuestionVersion(item.getQuestionVersion());
        row.setAnswerPayload(JSON.createObjectNode().put("text", "answer")); row.setMarkingStatus(status);
        row.setCorrect(status == MarkingStatus.AUTO ? correct : null); row.setConfirmedScore(score); answers.saveAndFlush(row);
    }
    private JsonNode findTopic(JsonNode response, String name) {
        for (JsonNode row : response.path("topics")) if (name.equals(row.path("topicName").asText())) return row;
        throw new AssertionError("Missing topic " + name);
    }
    private void assertTopic(JsonNode response, String name, int count, String earned, String available, String percent, String band) {
        JsonNode row = findTopic(response, name); assertThat(row.path("confirmedItemCount").asInt()).isEqualTo(count);
        assertThat(row.path("earnedMarks").decimalValue()).isEqualByComparingTo(earned);
        assertThat(row.path("availableMarks").decimalValue()).isEqualByComparingTo(available);
        assertThat(row.path("scorePercent").decimalValue()).isEqualByComparingTo(percent);
        assertThat(row.path("band").asText()).isEqualTo(band);
    }
}
