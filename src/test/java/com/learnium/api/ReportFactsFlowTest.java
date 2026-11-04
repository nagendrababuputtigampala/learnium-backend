package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.report.ReportFactsService;
import com.learnium.report.ReportFactsService.BatchTopicFact;
import com.learnium.report.ReportFactsService.StudentFacts;
import com.learnium.report.ReportFactsService.TopicFact;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

/**
 * Checkpoint A for M6 (task 71): report facts come from attempt facts — a
 * student in two batches proves the batch scope, pending marking is excluded
 * from both sides of every percentage, and invalidated attempts never count.
 */
@Transactional
class ReportFactsFlowTest extends PublishingFixtures {
    @Autowired ReportFactsService facts;
    @Autowired com.learnium.repository.exam.ExamAttemptRepository attempts;
    @Autowired com.learnium.repository.exam.QuestionAttemptRepository answers;

    private ExamInstance instanceA;
    private ExamInstance instanceB;
    private Batch batchB;
    private BigDecimal mcqMarks;
    private final Instant base = Instant.parse("2026-11-03T10:00:00Z");
    private final Map<UUID, Integer> attemptNos = new HashMap<>();

    @BeforeEach
    void reportSetup() throws Exception {
        JsonNode definition = create();
        instanceA = instance(publish(definition.path("id").asText(), publishBody()));
        batchB = new Batch();
        batchB.setCentre(centre);
        batchB.setName("Batch B2");
        batchB.setGradeSubject(pairing);
        batchB = batches.saveAndFlush(batchB);
        member(batchB, owner);
        member(batchB, student);
        Map<String, Object> secondPublish = publishBody();
        secondPublish.put("batchId", batchB.getBatchId());
        instanceB = instance(publish(definition.path("id").asText(), secondPublish));

        Topic algebra = topic("Algebra");
        Topic geometry = topic("Geometry");
        ExamInstanceQuestion algebraA = writtenItem(instanceA, algebra, 10, 21);
        ExamInstanceQuestion algebraB = writtenItem(instanceB, algebra, 10, 21);
        ExamInstanceQuestion geometryA = writtenItem(instanceA, geometry, 10, 22);
        ExamInstanceQuestion mcqA = frozen.frozen(centre.getCentreId(), instanceA.getExamInstanceId()).stream()
                .filter(item -> item.getQuestion().getQuestionId().equals(mcq.getQuestionId()))
                .findFirst()
                .orElseThrow();
        mcqMarks = mcqA.getMarks();

        confirmed(student, instanceA, algebraA, "8.00", 100);
        objective(student, instanceA, mcqA, true, 200);
        confirmed(student, instanceB, algebraB, "6.00", 300);
        pending(attempt(student, instanceB, 400, ExamAttemptStatus.SUBMITTED), algebraB);
        pending(attempt(student, instanceA, 600, ExamAttemptStatus.SUBMITTED), geometryA);
        ExamAttempt invalid = attempt(student, instanceA, 500, ExamAttemptStatus.INVALIDATED);
        answer(invalid, algebraA, MarkingStatus.CONFIRMED, false, new BigDecimal("10.00"));

        UserAccount peer = user(centre, UserRole.STUDENT);
        member(batch, peer);
        confirmed(peer, instanceA, algebraA, "2.00", 150);
    }

    @Test
    void studentFactsSpanBatchesExcludePendingAndInvalidated() {
        StudentFacts all = facts.studentFacts(centre.getCentreId(), student.getUserId(), null);

        TopicFact algebra = topicNamed(all, "Algebra");
        assertThat(algebra.confirmedItems()).isEqualTo(2);
        assertThat(algebra.pendingItems()).isEqualTo(1);
        assertThat(algebra.earnedMarks()).isEqualByComparingTo("14.00");
        assertThat(algebra.availableMarks()).isEqualByComparingTo("20");
        assertThat(algebra.percentage()).isEqualByComparingTo("70.00");
        assertThat(algebra.band()).isNull();
        TopicFact fractions = topicNamed(all, "Fractions");
        assertThat(fractions.confirmedItems()).isEqualTo(1);
        assertThat(fractions.percentage()).isEqualByComparingTo("100.00");
        assertThat(all.topics()).extracting(TopicFact::topicName).doesNotContain("Geometry");

        assertThat(all.overall().confirmedItems()).isEqualTo(3);
        assertThat(all.overall().pendingItems()).isEqualTo(2);
        assertThat(all.overall().earnedMarks()).isEqualByComparingTo(new BigDecimal("14.00").add(mcqMarks));
        assertThat(all.overall().availableMarks()).isEqualByComparingTo(new BigDecimal("20").add(mcqMarks));
        assertThat(all.overall().markingCoveragePercent()).isEqualByComparingTo("60.00");

        assertThat(all.tests()).hasSize(5);
        assertThat(all.tests().get(0).percentage()).isEqualByComparingTo("80.00");
        assertThat(all.tests().get(3).testName()).isNotNull();
        assertThat(all.tests().get(4).pendingItems()).isEqualTo(1);
        assertThat(all.tests().get(4).availableMarks()).isEqualByComparingTo("0");
    }

    @Test
    void studentFactsScopedToOneBatch() {
        StudentFacts scoped = facts.studentFacts(centre.getCentreId(), student.getUserId(), batch.getBatchId());

        assertThat(scoped.topics()).extracting(TopicFact::topicName).containsExactly("Algebra", "Fractions");
        TopicFact algebra = topicNamed(scoped, "Algebra");
        assertThat(algebra.confirmedItems()).isEqualTo(1);
        assertThat(algebra.earnedMarks()).isEqualByComparingTo("8.00");
        assertThat(algebra.pendingItems()).isZero();
        assertThat(scoped.overall().pendingItems()).isEqualTo(1);
        assertThat(scoped.tests()).hasSize(3);
        assertThat(scoped.tests()).allSatisfy(test -> assertThat(test.batchName()).isEqualTo(batch.getName()));
    }

    @Test
    void batchFactsAggregateStudentsWeakestFirstWithNoDataTopicsLast() {
        var rows = facts.batchTopicFacts(centre.getCentreId(), batch.getBatchId());

        assertThat(rows).extracting(BatchTopicFact::topicName).containsExactly("Algebra", "Fractions", "Geometry");
        BatchTopicFact algebra = rows.get(0);
        assertThat(algebra.confirmedItems()).isEqualTo(2);
        assertThat(algebra.studentCount()).isEqualTo(2);
        assertThat(algebra.earnedMarks()).isEqualByComparingTo("10.00");
        assertThat(algebra.availableMarks()).isEqualByComparingTo("20");
        assertThat(algebra.percentage()).isEqualByComparingTo("50.00");
        BatchTopicFact fractions = rows.get(1);
        assertThat(fractions.studentCount()).isEqualTo(1);
        assertThat(fractions.percentage()).isEqualByComparingTo("100.00");
        BatchTopicFact geometry = rows.get(2);
        assertThat(geometry.confirmedItems()).isZero();
        assertThat(geometry.pendingItems()).isEqualTo(1);
        assertThat(geometry.studentCount()).isZero();
        assertThat(geometry.percentage()).isNull();
        assertThat(geometry.band()).isNull();
    }

    @Test
    void factsForAnUnknownStudentAreEmpty() {
        StudentFacts empty = facts.studentFacts(centre.getCentreId(), UUID.randomUUID(), null);
        assertThat(empty.topics()).isEmpty();
        assertThat(empty.tests()).isEmpty();
        assertThat(empty.overall().confirmedItems()).isZero();
        assertThat(empty.overall().markingCoveragePercent()).isEqualByComparingTo("100");
        assertThat(facts.batchTopicFacts(centre.getCentreId(), UUID.randomUUID())).isEmpty();
    }

    private TopicFact topicNamed(StudentFacts facts, String name) {
        return facts.topics().stream()
                .filter(topic -> name.equals(topic.topicName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing topic " + name));
    }

    private ExamInstance instance(JsonNode published) {
        return instances.findById(UUID.fromString(published.path("id").asText())).orElseThrow();
    }

    private Topic topic(String name) {
        Topic value = new Topic();
        value.setGradeSubject(pairing);
        value.setTopicName(name);
        return topics.saveAndFlush(value);
    }

    private ExamInstanceQuestion writtenItem(ExamInstance instance, Topic itemTopic, int marks, int order)
            throws Exception {
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

    private void confirmed(UserAccount user, ExamInstance instance, ExamInstanceQuestion item, String score, int seconds) {
        ExamAttempt attempt = attempt(user, instance, seconds, ExamAttemptStatus.SUBMITTED);
        answer(attempt, item, MarkingStatus.CONFIRMED, false, new BigDecimal(score));
    }

    private void objective(UserAccount user, ExamInstance instance, ExamInstanceQuestion item, boolean correct, int seconds) {
        ExamAttempt attempt = attempt(user, instance, seconds, ExamAttemptStatus.AUTO_SUBMITTED);
        answer(attempt, item, MarkingStatus.AUTO, correct, null);
    }

    private ExamAttempt attempt(UserAccount user, ExamInstance instance, int seconds, ExamAttemptStatus status) {
        int attemptNo = attemptNos.merge(instance.getExamInstanceId(), 1, Integer::sum);
        ExamAttempt row = new ExamAttempt();
        row.setCentreId(centre.getCentreId());
        row.setExamInstance(instance);
        row.setUser(user);
        row.setAttemptNo(attemptNo);
        row.setStartedAt(base.plusSeconds(seconds).minusSeconds(60));
        row.setDeadlineAt(base.plusSeconds(seconds));
        row.setSubmittedAt(base.plusSeconds(seconds));
        row.setStatus(status);
        return attempts.saveAndFlush(row);
    }

    private void pending(ExamAttempt attempt, ExamInstanceQuestion item) {
        answer(attempt, item, MarkingStatus.PENDING, false, null);
    }

    private void answer(ExamAttempt attempt, ExamInstanceQuestion item, MarkingStatus status, boolean correct, BigDecimal score) {
        QuestionAttempt row = new QuestionAttempt();
        row.setCentreId(centre.getCentreId());
        row.setAttempt(attempt);
        row.setExamInstanceQuestion(item);
        row.setQuestion(item.getQuestion());
        row.setQuestionVersion(item.getQuestionVersion());
        row.setAnswerPayload(JSON.createObjectNode().put("text", "answer"));
        row.setMarkingStatus(status);
        row.setCorrect(status == MarkingStatus.AUTO ? correct : null);
        row.setConfirmedScore(score);
        answers.saveAndFlush(row);
    }
}
