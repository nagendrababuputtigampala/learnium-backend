package com.learnium.exam;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnium.common.error.BusinessException;
import com.learnium.marking.MarkingJobService;
import com.learnium.model.content.Question;
import com.learnium.model.exam.*;
import com.learnium.model.master.QuestionType;
import com.learnium.model.user.UserAccount;
import com.learnium.repository.exam.*;
import com.learnium.security.LearniumPrincipal;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class StudentAttemptServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ExamInstanceStudentRepository assignments = mock(ExamInstanceStudentRepository.class);
    private final ExamInstanceRepository instances = mock(ExamInstanceRepository.class);
    private final ExamAttemptRepository attempts = mock(ExamAttemptRepository.class);
    private final ExamInstanceQuestionRepository items = mock(ExamInstanceQuestionRepository.class);
    private final QuestionAttemptRepository answers = mock(QuestionAttemptRepository.class);
    private final Instant now = Instant.parse("2026-11-03T18:00:00Z");
    private final UUID centreId = UUID.randomUUID(), userId = UUID.randomUUID();
    private final LearniumPrincipal caller = LearniumPrincipal.student(userId, centreId);
    private StudentAttemptService service;

    @BeforeEach void setup() {
        service = new StudentAttemptService(assignments, instances, attempts, items, answers,
                mock(MarkingJobService.class), mock(com.learnium.exam.ProctorService.class),
                Clock.fixed(now, ZoneOffset.UTC), new tools.jackson.databind.ObjectMapper());
    }

    @Test void canonicalAnswersAcceptScalarAndObjectFormsAndRejectEveryMalformedShape() {
        assertThat(canonical("MCQ", JSON.getNodeFactory().textNode("a")).path("optionId").asText()).isEqualTo("a");
        assertThat(canonical("FILL_BLANK", JSON.getNodeFactory().textNode("fifty")).path("text").asText()).isEqualTo("fifty");
        assertThat(canonical("NUMERIC", JSON.getNodeFactory().numberNode(new BigDecimal("2.5"))).path("number").decimalValue())
                .isEqualByComparingTo("2.5");

        assertThatThrownBy(() -> canonical("MCQ", null)).isInstanceOf(BusinessException.class);
        for (JsonNode bad : List.of(JSON.nullNode(), JSON.createObjectNode(), JSON.getNodeFactory().numberNode(1), JSON.getNodeFactory().textNode(" ")))
            assertThatThrownBy(() -> canonical("MCQ", bad)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> canonical("WRITTEN", JSON.createObjectNode())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> canonical("WRITTEN", JSON.getNodeFactory().numberNode(1))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> canonical("WRITTEN", JSON.getNodeFactory().textNode("x".repeat(20001)))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> canonical("NUMERIC", JSON.createObjectNode())).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> canonical("NUMERIC", JSON.getNodeFactory().textNode("2"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> canonical("FLASHCARD", JSON.getNodeFactory().textNode("x"))).isInstanceOf(BusinessException.class);
    }

    @Test void frozenFallbackKeysAndNumericToleranceCoverIncorrectAndUnsupportedAnswers() {
        ExamInstanceQuestion fibMissing = item("FILL_BLANK", JSON.createObjectNode(), "Fifty", 1);
        ExamInstanceQuestion fibWrongType = item("FILL_BLANK", JSON.createObjectNode().put("answer", 50), "Fifty", 2);
        assertThat(grade(fibMissing, JSON.createObjectNode().put("text", " fifty "))).isTrue();
        assertThat(grade(fibWrongType, JSON.createObjectNode().put("text", "FIFTY"))).isTrue();

        ExamInstanceQuestion numberMissing = item("NUMERIC", JSON.createObjectNode(), "2", 3);
        ExamInstanceQuestion numberWrongTypes = item("NUMERIC",
                JSON.createObjectNode().put("answerValue", "2").put("tolerance", "none"), "2", 4);
        assertThat(grade(numberMissing, JSON.createObjectNode().put("number", 2))).isTrue();
        assertThat(grade(numberWrongTypes, JSON.createObjectNode().put("number", 3))).isFalse();
        assertThat(grade(numberMissing, JSON.createObjectNode().put("number", 3))).isFalse();
        assertThat(grade(numberMissing, JSON.createObjectNode().put("number", "two"))).isFalse();
        assertThat(grade(item("FLASHCARD", JSON.createObjectNode(), "", 5), JSON.createObjectNode())).isFalse();
    }

    @Test void publicGuardsCoverClosedAttemptsWrongKindsOwnershipAndPreSubmitResults() {
        ExamAttempt attempt = attempt(ExamAttemptStatus.SUBMITTED, now.plusSeconds(60));
        when(attempts.lockOwned(centreId, attempt.getAttemptId(), userId)).thenReturn(Optional.of(attempt));
        assertThatThrownBy(() -> service.save(caller, attempt.getAttemptId(), UUID.randomUUID(), web("{}")))
                .isInstanceOf(BusinessException.class);

        attempt.setStatus(ExamAttemptStatus.IN_PROGRESS);
        attempt.getExamInstance().setClosedAt(now);
        assertThatThrownBy(() -> service.save(caller, attempt.getAttemptId(), UUID.randomUUID(), web("{}")))
                .isInstanceOf(BusinessException.class);

        when(attempts.findById(attempt.getAttemptId())).thenReturn(Optional.of(attempt));
        assertThatThrownBy(() -> service.result(caller, attempt.getAttemptId())).isInstanceOf(BusinessException.class);
        UserAccount stranger = user(UUID.randomUUID()); attempt.setUser(stranger);
        assertThatThrownBy(() -> service.attempt(caller, attempt.getAttemptId())).isInstanceOf(BusinessException.class);
        attempt.setUser(user(userId)); attempt.setCentreId(UUID.randomUUID());
        assertThatThrownBy(() -> service.attempt(caller, attempt.getAttemptId())).isInstanceOf(BusinessException.class);

        ExamInstance practice = instance("SCORED_TEST", now.minusSeconds(1), now.plusSeconds(60));
        when(assignments.findAssigned(centreId, practice.getExamInstanceId(), userId))
                .thenReturn(Optional.of(mock(ExamInstanceStudent.class)));
        when(instances.findById(practice.getExamInstanceId())).thenReturn(Optional.of(practice));
        assertThatThrownBy(() -> service.practice(caller, practice.getExamInstanceId())).isInstanceOf(BusinessException.class);
        practice.setDefinitionKind("FLASHCARD_PRACTICE"); practice.setClosedAt(now);
        assertThatThrownBy(() -> service.practice(caller, practice.getExamInstanceId())).isInstanceOf(BusinessException.class);
        practice.setClosedAt(null); practice.setOpensAt(now.plusSeconds(1));
        assertThatThrownBy(() -> service.practice(caller, practice.getExamInstanceId())).isInstanceOf(BusinessException.class);
        practice.setOpensAt(now.minusSeconds(2)); practice.setClosesAt(now.minusSeconds(1));
        assertThatThrownBy(() -> service.practice(caller, practice.getExamInstanceId())).isInstanceOf(BusinessException.class);
    }

    @Test void resultRowsHandleMissingAnswersAndConfirmedWrittenAnswersWithoutScores() {
        ExamAttempt attempt = attempt(ExamAttemptStatus.SUBMITTED, now.plusSeconds(60));
        attempt.getExamInstance().setAnswerReleasePolicy("ON_SUBMIT");
        ExamInstanceQuestion confirmedItem = item("WRITTEN", JSON.createObjectNode().put("modelAnswer", "model"), "", 1);
        ExamInstanceQuestion missingWritten = item("WRITTEN", JSON.createObjectNode(), "", 2);
        ExamInstanceQuestion missingMcq = item("MCQ", JSON.createObjectNode(), "a", 3);
        QuestionAttempt confirmed = answer(attempt, confirmedItem); confirmed.setMarkingStatus(MarkingStatus.CONFIRMED);
        confirmed.setConfirmedScore(null); confirmed.setMarkerFeedback("feedback");
        when(items.frozen(centreId, attempt.getExamInstance().getExamInstanceId()))
                .thenReturn(List.of(confirmedItem, missingWritten, missingMcq));
        when(answers.findByCentreIdAndAttemptId(centreId, attempt.getAttemptId())).thenReturn(List.of(confirmed));

        com.learnium.api.dto.StudentDtos.AttemptResultDto result =
                ReflectionTestUtils.invokeMethod(service, "resultDto", attempt);
        assertThat(result.items()).extracting(com.learnium.api.dto.StudentDtos.ResultItemDto::state)
                .containsExactly("CONFIRMED", "PENDING_MARKING", "INCORRECT");
        assertThat(result.score()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.markingPending()).isTrue();
    }

    @Test void startUsesCloseWhenItPrecedesTheTimeLimitAndRejectsAnOpenPracticeInstance() {
        ExamInstance instance = instance("SCORED_TEST", now.minusSeconds(1), now.plusSeconds(30));
        ExamInstanceStudent assignment = mock(ExamInstanceStudent.class); when(assignment.getUser()).thenReturn(user(userId));
        when(assignments.findAssigned(centreId, instance.getExamInstanceId(), userId)).thenReturn(Optional.of(assignment));
        when(instances.lockInCentre(centreId, instance.getExamInstanceId())).thenReturn(Optional.of(instance));
        when(attempts.ownedForInstance(centreId, instance.getExamInstanceId(), userId)).thenReturn(List.of());
        when(items.frozen(centreId, instance.getExamInstanceId())).thenReturn(List.of());
        when(attempts.saveAndFlush(any())).thenAnswer(invocation -> {
            ExamAttempt value = invocation.getArgument(0); ReflectionTestUtils.setField(value, "attemptId", UUID.randomUUID()); return value;
        });
        instance.setTimeLimitSeconds(60);
        assertThat(service.start(caller, instance.getExamInstanceId()).deadlineAt()).isEqualTo(now.plusSeconds(30));

        instance.setDefinitionKind("FLASHCARD_PRACTICE");
        assertThatThrownBy(() -> service.start(caller, instance.getExamInstanceId())).isInstanceOf(BusinessException.class);
    }

    @Test void invalidWebJsonIsConvertedToTheStandardAnswerValidationError() {
        tools.jackson.databind.JsonNode invalid = mock(tools.jackson.databind.JsonNode.class);
        when(invalid.toString()).thenReturn("{");
        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(service, "internal", invalid))
                .isInstanceOf(BusinessException.class);
    }

    private JsonNode canonical(String type, JsonNode value) {
        return ReflectionTestUtils.invokeMethod(service, "canonicalAnswer", type, value);
    }
    private boolean grade(ExamInstanceQuestion item, JsonNode answer) {
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(service, "grade", item, answer));
    }
    private tools.jackson.databind.JsonNode web(String value) { return new tools.jackson.databind.ObjectMapper().readTree(value); }
    private ExamInstance instance(String kind, Instant opens, Instant closes) {
        ExamInstance value = new ExamInstance(); ReflectionTestUtils.setField(value, "examInstanceId", UUID.randomUUID());
        value.setCentreId(centreId); value.setDefinitionKind(kind); value.setDefinitionName("Test"); value.setOpensAt(opens);
        value.setClosesAt(closes); value.setMaxAttempts(2); value.setAnswerReleasePolicy("NEVER"); return value;
    }
    private ExamAttempt attempt(ExamAttemptStatus status, Instant deadline) {
        ExamAttempt value = new ExamAttempt(); ReflectionTestUtils.setField(value, "attemptId", UUID.randomUUID());
        value.setCentreId(centreId); value.setUser(user(userId)); value.setExamInstance(instance("SCORED_TEST", now.minusSeconds(1), now.plusSeconds(120)));
        value.setStatus(status); value.setStartedAt(now.minusSeconds(5)); value.setDeadlineAt(deadline); return value;
    }
    private UserAccount user(UUID id) {
        UserAccount value = new UserAccount(); ReflectionTestUtils.setField(value, "userId", id); return value;
    }
    private ExamInstanceQuestion item(String type, JsonNode payload, String correctRef, int order) {
        QuestionType questionType = new QuestionType(); questionType.setCode(type);
        Question question = new Question(); question.setQuestionType(questionType);
        ExamInstanceQuestion value = new ExamInstanceQuestion();
        ReflectionTestUtils.setField(value, "examInstanceQuestionId", UUID.randomUUID());
        value.setCentreId(centreId); value.setQuestion(question); value.setQuestionOrder(order); value.setQuestionVersion(1);
        value.setPayloadSnapshot(payload); value.setCorrectRefSnapshot(correctRef); value.setMarks(BigDecimal.ONE); return value;
    }
    private QuestionAttempt answer(ExamAttempt attempt, ExamInstanceQuestion item) {
        QuestionAttempt value = new QuestionAttempt(); ReflectionTestUtils.setField(value, "questionAttemptId", UUID.randomUUID());
        value.setCentreId(centreId); value.setAttempt(attempt); value.setExamInstanceQuestion(item); value.setQuestion(item.getQuestion());
        value.setQuestionVersion(1); value.setAnswerPayload(JSON.createObjectNode().put("text", "answer")); return value;
    }
}
