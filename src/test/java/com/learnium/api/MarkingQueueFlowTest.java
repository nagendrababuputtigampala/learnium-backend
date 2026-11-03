package com.learnium.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnium.api.dto.MarkingQueueDtos.*;
import com.learnium.common.error.*;
import com.learnium.marking.*;
import com.learnium.model.content.*;
import com.learnium.model.exam.*;
import com.learnium.repository.content.BackgroundJobRepository;
import com.learnium.repository.exam.*;
import com.learnium.security.LearniumPrincipal;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class MarkingQueueFlowTest extends PublishingFixtures {
    @MockitoBean Clock clock;
    @Autowired ExamAttemptRepository examAttempts;
    @Autowired QuestionAttemptRepository answers;
    @Autowired BackgroundJobRepository jobs;
    @Autowired MarkingJobService markingJobs;
    @Autowired MarkingQueueService markingQueue;
    @Autowired MarkingProperties markingProperties;
    private final Instant now = Instant.parse("2026-11-03T20:00:00Z");
    QuestionAttempt answer;

    @BeforeEach void queueSetup() throws Exception {
        org.mockito.Mockito.when(clock.instant()).thenReturn(now);
        markingProperties.setProvider("ollama"); markingProperties.setModel("queue-model");
        JsonNode published = publish(create().path("id").asText(), publishBody());
        ExamInstance instance = instances.findById(UUID.fromString(published.path("id").asText())).orElseThrow();
        ExamInstanceQuestion item = frozen.frozen(centre.getCentreId(), instance.getExamInstanceId()).stream()
                .filter(row -> row.getQuestion().getQuestionId().equals(written.getQuestionId())).findFirst().orElseThrow();
        item.setMarks(new BigDecimal("10.00"));
        item.setPayloadSnapshot(JSON.readTree("{\"stem\":\"Explain\",\"modelAnswer\":\"Model\",\"rubric\":["
                + "{\"id\":\"reasoning\",\"criterion\":\"Reasoning\",\"maxScore\":10},"
                + "{\"id\":\"clarity\",\"criterion\":\"Clarity\",\"maxScore\":5},"
                + "{\"id\":\"evidence\",\"criterion\":\"Evidence\",\"maxScore\":5}],\"maxScore\":20}"));
        frozen.saveAndFlush(item);
        ExamAttempt attempt = new ExamAttempt(); attempt.setCentreId(centre.getCentreId()); attempt.setExamInstance(instance);
        attempt.setUser(student); attempt.setStartedAt(now.minusSeconds(300)); attempt.setDeadlineAt(now.plusSeconds(300));
        attempt.setSubmittedAt(now.minusSeconds(60)); attempt.setStatus(ExamAttemptStatus.SUBMITTED);
        attempt = examAttempts.saveAndFlush(attempt);
        answer = new QuestionAttempt(); answer.setCentreId(centre.getCentreId()); answer.setAttempt(attempt);
        answer.setExamInstanceQuestion(item); answer.setQuestion(written); answer.setQuestionVersion(written.getVersion());
        answer.setCreatedBy(student.getUserId()); answer.setAnswerPayload(JSON.createObjectNode().put("text", "My explanation"));
        answer.setMarkingStatus(MarkingStatus.PENDING); answer = answers.saveAndFlush(answer);
    }

    @AfterEach void resetMarking() { markingProperties.setProvider(""); markingProperties.setModel(""); }

    @Test void queueIsScopedToManagingTeachersAndCentreAdmins() throws Exception {
        mvc.perform(get("/api/v1/marking-queue").with(as(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].items[0].questionAttemptId").value(answer.getQuestionAttemptId().toString()))
                .andExpect(jsonPath("$[0].items[0].suggestionState").value("NONE"));
        mvc.perform(get("/api/v1/marking-queue").with(as(colleague))).andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mvc.perform(get("/api/v1/marking-queue").with(as(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].items.length()").value(1));
        mvc.perform(get("/api/v1/marking-queue/" + answer.getQuestionAttemptId()).with(as(colleague)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/marking-queue").with(as(student))).andExpect(status().isForbidden());
    }

    @Test void queueReportsMarkingFailedAndReadySuggestionStatesAndFrozenDetail() throws Exception {
        UUID jobId = markingJobs.enqueueMarking(answer.getQuestionAttemptId());
        assertState("MARKING");
        BackgroundJob job = jobs.findById(jobId).orElseThrow(); job.setStatus(BackgroundJobStatus.RUNNING);
        jobs.saveAndFlush(job); assertState("MARKING");
        job.setStatus(BackgroundJobStatus.FAILED);
        job.setFinishedAt(now); jobs.saveAndFlush(job); assertState("FAILED");
        job.setStatus(BackgroundJobStatus.SUCCEEDED); jobs.saveAndFlush(job); assertState("NONE");
        answer.setAiBreakdown(JSON.readTree("{\"criteria\":{\"reasoning\":{\"score\":8}},\"feedback\":\"Strong\"}"));
        answers.saveAndFlush(answer); assertState("READY");
        mvc.perform(get("/api/v1/marking-queue/" + answer.getQuestionAttemptId()).with(as(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.payload.rubric[0].id").value("reasoning"))
                .andExpect(jsonPath("$.answer.text").value("My explanation"))
                .andExpect(jsonPath("$.aiFeedback").value("Strong"));
    }

    @Test void manualConfirmationScalesRoundsPersistsAuditAndRejectsSecondConfirmation() throws Exception {
        String request = body(Map.of("criteria", List.of(
                Map.of("criterionId", "reasoning", "score", 3.33),
                Map.of("criterionId", "clarity", "score", 1.67),
                Map.of("criterionId", "evidence", "score", 1.67)), "feedback", "Keep explaining"));
        mvc.perform(post("/api/v1/marking-queue/" + answer.getQuestionAttemptId() + "/confirm").with(as(owner))
                        .contentType("application/json").content(request))
                .andExpect(status().isOk()).andExpect(jsonPath("$.confirmedScore").value(3.34));
        QuestionAttempt stored = answers.findById(answer.getQuestionAttemptId()).orElseThrow();
        assertThat(stored.getConfirmedScore()).isEqualByComparingTo("3.34");
        assertThat(stored.getMarkedBy()).isEqualTo(owner.getUserId()); assertThat(stored.getMarkedAt()).isEqualTo(now);
        assertThat(stored.getConfirmedBreakdown().path("criteria").path("clarity").path("score").decimalValue())
                .isEqualByComparingTo("1.67");
        assertThat(jobs.findLatestByQuestionAttemptId(answer.getQuestionAttemptId(), org.springframework.data.domain.PageRequest.of(0, 1))).isEmpty();
        mvc.perform(post("/api/v1/marking-queue/" + answer.getQuestionAttemptId() + "/confirm").with(as(owner))
                        .contentType("application/json").content(request))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LRN-MRK-002"));
    }

    @Test void confirmationRejectsMissingDuplicateUnknownAndOutOfBoundsCriteria() throws Exception {
        List<Object> invalid = List.of(
                Map.of("criteria", List.of(Map.of("criterionId", "reasoning", "score", 5))),
                Map.of("criteria", List.of(Map.of("criterionId", "reasoning", "score", 5), Map.of("criterionId", "reasoning", "score", 5), Map.of("criterionId", "evidence", "score", 2))),
                Map.of("criteria", List.of(Map.of("criterionId", "reasoning", "score", 5), Map.of("criterionId", "clarity", "score", 2), Map.of("criterionId", "forged", "score", 2))),
                Map.of("criteria", List.of(Map.of("criterionId", "reasoning", "score", -1), Map.of("criterionId", "clarity", "score", 2), Map.of("criterionId", "evidence", "score", 2))),
                Map.of("criteria", List.of(Map.of("criterionId", "reasoning", "score", 11), Map.of("criterionId", "clarity", "score", 2), Map.of("criterionId", "evidence", "score", 2))),
                Map.of("criteria", List.of(Map.of("criterionId", "reasoning", "score", 5), Map.of("criterionId", "clarity", "score", 2), Map.of("criterionId", "evidence", "score", 2)), "feedback", "x".repeat(4001)));
        for (Object request : invalid)
            mvc.perform(post("/api/v1/marking-queue/" + answer.getQuestionAttemptId() + "/confirm").with(as(owner))
                            .contentType("application/json").content(body(request)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LRN-MRK-001"));
        assertThat(answers.findById(answer.getQuestionAttemptId()).orElseThrow().getMarkingStatus()).isEqualTo(MarkingStatus.PENDING);
    }

    @Test void rerunRequiresLatestFailedJobAndCreatesFreshLiveJob() throws Exception {
        assertThatThrownBy(() -> markingJobs.rerunMarking(UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
        mvc.perform(post("/api/v1/marking-queue/" + answer.getQuestionAttemptId() + "/rerun").with(as(owner)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LRN-MRK-003"));
        UUID firstId = markingJobs.enqueueMarking(answer.getQuestionAttemptId());
        mvc.perform(post("/api/v1/marking-queue/" + answer.getQuestionAttemptId() + "/rerun").with(as(owner)))
                .andExpect(status().isConflict());
        BackgroundJob first = jobs.findById(firstId).orElseThrow(); first.setStatus(BackgroundJobStatus.FAILED);
        first.setFinishedAt(now); jobs.saveAndFlush(first);
        mvc.perform(post("/api/v1/marking-queue/" + answer.getQuestionAttemptId() + "/rerun").with(as(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.suggestionState").value("MARKING"));
        assertThat(jobs.findLatestByQuestionAttemptId(answer.getQuestionAttemptId(), org.springframework.data.domain.PageRequest.of(0, 10))).hasSize(2);
        assertState("MARKING");
        mvc.perform(post("/api/v1/marking-queue/" + answer.getQuestionAttemptId() + "/rerun").with(as(owner)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LRN-MRK-003"));
    }

    @Test void confirmHidesForeignAnswersAndMissingIds() throws Exception {
        String valid = body(Map.of("criteria", List.of(
                Map.of("criterionId", "reasoning", "score", 1),
                Map.of("criterionId", "clarity", "score", 1),
                Map.of("criterionId", "evidence", "score", 1))));
        mvc.perform(post("/api/v1/marking-queue/" + UUID.randomUUID() + "/confirm").with(as(owner))
                        .contentType("application/json").content(valid))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/marking-queue/" + answer.getQuestionAttemptId() + "/confirm").with(as(colleague))
                        .contentType("application/json").content(valid))
                .andExpect(status().isNotFound());
    }

    @Test void serviceRejectsEveryMalformedFrozenRubricAndNullCriterionShape() throws Exception {
        LearniumPrincipal caller = LearniumPrincipal.staff(owner.getUserId(), centre.getCentreId(), owner.getRole());
        assertInvalid(() -> markingQueue.confirm(caller, answer.getQuestionAttemptId(), null));
        assertInvalid(() -> markingQueue.confirm(caller, answer.getQuestionAttemptId(), new ConfirmRequest(null, null)));
        setRubric(JSON.readTree("{}"));
        assertInvalid(() -> markingQueue.confirm(caller, answer.getQuestionAttemptId(), new ConfirmRequest(List.of(), null)));
        setRubric(JSON.readTree("[]"));
        assertInvalid(() -> markingQueue.confirm(caller, answer.getQuestionAttemptId(), new ConfirmRequest(List.of(), null)));
        setRubric(JSON.readTree("[{\"id\":\"\",\"maxScore\":1}]"));
        assertInvalid(() -> markingQueue.confirm(caller, answer.getQuestionAttemptId(), new ConfirmRequest(List.of(), null)));
        setRubric(JSON.readTree("[{\"id\":\"x\",\"maxScore\":\"bad\"}]"));
        assertInvalid(() -> markingQueue.confirm(caller, answer.getQuestionAttemptId(), new ConfirmRequest(List.of(), null)));
        setRubric(JSON.readTree("[{\"id\":\"x\",\"maxScore\":1},{\"id\":\"x\",\"maxScore\":1}]"));
        assertInvalid(() -> markingQueue.confirm(caller, answer.getQuestionAttemptId(), new ConfirmRequest(List.of(), null)));
        setRubric(JSON.readTree("[{\"id\":\"x\",\"maxScore\":0}]"));
        assertInvalid(() -> markingQueue.confirm(caller, answer.getQuestionAttemptId(),
                new ConfirmRequest(List.of(new CriterionScoreRequest("x", BigDecimal.ZERO)), null)));
        setRubric(JSON.readTree("[{\"id\":\"x\",\"maxScore\":1}]"));
        assertInvalid(() -> markingQueue.confirm(caller, answer.getQuestionAttemptId(),
                new ConfirmRequest(Arrays.asList((CriterionScoreRequest) null), null)));
        assertInvalid(() -> markingQueue.confirm(caller, answer.getQuestionAttemptId(),
                new ConfirmRequest(List.of(new CriterionScoreRequest(null, BigDecimal.ZERO)), null)));
        assertInvalid(() -> markingQueue.confirm(caller, answer.getQuestionAttemptId(),
                new ConfirmRequest(List.of(new CriterionScoreRequest("x", null)), null)));
    }

    @Test void confirmationWithoutFeedbackCanReturnReadyState() throws Exception {
        answer.setAiBreakdown(JSON.readTree("{\"criteria\":{}}")); answers.saveAndFlush(answer);
        MarkingDetailDto result = markingQueue.confirm(
                LearniumPrincipal.staff(owner.getUserId(), centre.getCentreId(), owner.getRole()),
                answer.getQuestionAttemptId(), new ConfirmRequest(List.of(
                        new CriterionScoreRequest("reasoning", BigDecimal.ONE),
                        new CriterionScoreRequest("clarity", BigDecimal.ONE),
                        new CriterionScoreRequest("evidence", BigDecimal.ONE)), null));
        assertThat(result.suggestionState()).isEqualTo("READY");
        assertThat(result.markerFeedback()).isNull();
    }

    @Test void rerunRejectsConfirmedAnswersAndFailedJobsWhenAnotherJobIsLive() {
        answer.setMarkingStatus(MarkingStatus.CONFIRMED); answers.saveAndFlush(answer);
        assertThatThrownBy(() -> markingJobs.rerunMarking(answer.getQuestionAttemptId()))
                .isInstanceOf(BusinessException.class);
        answer.setMarkingStatus(MarkingStatus.PENDING); answers.saveAndFlush(answer);
        UUID liveId = markingJobs.enqueueMarking(answer.getQuestionAttemptId());
        BackgroundJob live = jobs.findById(liveId).orElseThrow();
        BackgroundJob failed = new BackgroundJob(); failed.setJobType("MARKING");
        failed.setQuestionAttemptId(answer.getQuestionAttemptId()); failed.setMarkingInput(live.getMarkingInput().deepCopy());
        failed.setCentre(centre); failed.setProvider("ollama"); failed.setModel("failed");
        failed.setPromptVersion("marking-prompt-v1"); failed.setPayloadSchemaVersion("marking-v1");
        failed.setNextAttemptAt(now); failed.setCreatedBy(owner.getUserId()); failed.setStatus(BackgroundJobStatus.FAILED);
        jobs.saveAndFlush(failed);
        assertThatThrownBy(() -> markingJobs.rerunMarking(answer.getQuestionAttemptId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test void confirmRejectsAnAnswerThatIsNotAwaitingMarking() throws Exception {
        answer.setMarkingStatus(MarkingStatus.AUTO);
        answers.saveAndFlush(answer);
        String request = body(Map.of("criteria", List.of(
                Map.of("criterionId", "reasoning", "score", 5),
                Map.of("criterionId", "clarity", "score", 2),
                Map.of("criterionId", "evidence", "score", 2)), "feedback", "Nope"));
        mvc.perform(post("/api/v1/marking-queue/" + answer.getQuestionAttemptId() + "/confirm").with(as(owner))
                        .contentType("application/json").content(request))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LRN-MRK-001"));
    }

    @Test void queuePagesPendingAnswersBeforeGrouping() throws Exception {
        ExamAttempt secondAttempt = new ExamAttempt(); secondAttempt.setCentreId(centre.getCentreId());
        secondAttempt.setExamInstance(answer.getAttempt().getExamInstance()); secondAttempt.setUser(student);
        secondAttempt.setAttemptNo(2); secondAttempt.setStartedAt(now.minusSeconds(200));
        secondAttempt.setDeadlineAt(now.plusSeconds(300)); secondAttempt.setSubmittedAt(now.minusSeconds(30));
        secondAttempt.setStatus(ExamAttemptStatus.SUBMITTED);
        secondAttempt = examAttempts.saveAndFlush(secondAttempt);
        QuestionAttempt second = new QuestionAttempt(); second.setCentreId(centre.getCentreId());
        second.setAttempt(secondAttempt); second.setExamInstanceQuestion(answer.getExamInstanceQuestion());
        second.setQuestion(written); second.setQuestionVersion(written.getVersion());
        second.setCreatedBy(student.getUserId());
        second.setAnswerPayload(JSON.createObjectNode().put("text", "Another explanation"));
        second.setMarkingStatus(MarkingStatus.PENDING); answers.saveAndFlush(second);

        JsonNode all = json(mvc.perform(get("/api/v1/marking-queue").with(as(owner)))
                .andExpect(status().isOk()).andReturn());
        assertThat(all.get(0).path("items")).hasSize(2);
        JsonNode page0 = json(mvc.perform(get("/api/v1/marking-queue?page=0&size=1").with(as(owner)))
                .andExpect(status().isOk()).andReturn());
        assertThat(page0.get(0).path("items")).hasSize(1);
        JsonNode page1 = json(mvc.perform(get("/api/v1/marking-queue?page=1&size=1").with(as(owner)))
                .andExpect(status().isOk()).andReturn());
        assertThat(page1.get(0).path("items")).hasSize(1);
        assertThat(page0.get(0).path("items").get(0).path("questionAttemptId").asText())
                .isNotEqualTo(page1.get(0).path("items").get(0).path("questionAttemptId").asText());
        mvc.perform(get("/api/v1/marking-queue?size=0").with(as(owner)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LRN-VAL-001"));
        mvc.perform(get("/api/v1/marking-queue?page=-1").with(as(owner)))
                .andExpect(status().isBadRequest());
    }

    private void assertState(String state) throws Exception {
        mvc.perform(get("/api/v1/marking-queue").with(as(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].items[0].suggestionState").value(state));
    }

    private void setRubric(JsonNode rubric) {
        ObjectNode payload = JSON.createObjectNode(); payload.set("rubric", rubric);
        answer.getExamInstanceQuestion().setPayloadSnapshot(payload);
        frozen.saveAndFlush(answer.getExamInstanceQuestion());
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.MRK_INVALID));
    }
}
