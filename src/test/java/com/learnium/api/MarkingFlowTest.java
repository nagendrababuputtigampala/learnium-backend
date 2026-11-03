package com.learnium.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnium.generation.*;
import com.learnium.marking.*;
import com.learnium.model.content.*;
import com.learnium.model.exam.*;
import com.learnium.repository.content.BackgroundJobRepository;
import com.learnium.repository.exam.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Real claim/persistence transactions, with a model-free transport. */
class MarkingFlowTest extends PublishingFixtures {
    @MockitoBean ModelTransport transport;
    @Autowired MarkingJobService markingJobs;
    @Autowired MarkingWorker worker;
    @Autowired MarkingProperties markingProperties;
    @Autowired GenerationProperties generation;
    @Autowired GenerationClaimService generationClaims;
    @Autowired BackgroundJobRepository jobs;
    @Autowired ExamAttemptRepository examAttempts;
    @Autowired QuestionAttemptRepository answers;
    @Autowired JdbcTemplate jdbc;
    QuestionAttempt answer;
    JsonNode publishedInstance;
    @BeforeEach void markingSetup() throws Exception {
        markingProperties.setProvider("ollama"); markingProperties.setModel("frozen-marking-model");
        var instance = publish(create().path("id").asText(), publishBody());
        publishedInstance = instance;
        var attempt = new ExamAttempt(); attempt.setCentreId(centre.getCentreId());
        attempt.setExamInstance(instances.findById(UUID.fromString(instance.path("id").asText())).orElseThrow());
        attempt.setUser(student); attempt.setStartedAt(Instant.now()); examAttempts.saveAndFlush(attempt);
        answer = new QuestionAttempt(); answer.setCentreId(centre.getCentreId()); answer.setAttempt(attempt);
        answer.setExamInstanceQuestion(frozen.frozen(centre.getCentreId(), attempt.getExamInstance().getExamInstanceId()).stream()
                .filter(item -> item.getQuestion().getQuestionId().equals(written.getQuestionId())).findFirst().orElseThrow());
        answer.setQuestion(written); answer.setQuestionVersion(written.getVersion()); answer.setCreatedBy(student.getUserId());
        answer.setAnswerPayload(JSON.createObjectNode().put("text", "Two equal parts.")); answer.setMarkingStatus(MarkingStatus.MARKING_PENDING);
        answer = answers.saveAndFlush(answer);
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new ModelTransport.ModelResponse(200, envelope(false));
        }).when(transport).send(any());
    }
    @Test void committedPublishResponseExactlyMatchesReloadedInstance() throws Exception {
        var reloaded = json(mvc.perform(get("/api/v1/instances/" + publishedInstance.path("id").asText())
                .with(as(owner))).andExpect(status().isOk()).andReturn());
        assertThat(reloaded).isEqualTo(publishedInstance);
    }
    String envelope(boolean unknown) {
        var out = JSON.createObjectNode().put("feedback", "Clear explanation"); var criteria = out.putArray("criteria");
        for (int i = 1; i <= 3; i++) criteria.addObject().put("criterionId", unknown && i == 3 ? "forged" : "crit-" + i).put("score", i == 1 ? 0.5 : 1).put("feedback", "Good");
        var root = JSON.createObjectNode(); root.putObject("message").put("content", out.toString()); return root.toString();
    }
    @AfterEach void clean() {
        markingProperties.setProvider(""); markingProperties.setModel("");
        if (centre == null) return;
        for (String table : List.of("background_job", "exam_instance_student", "question_attempts", "exam_attempts", "exam_instance_questions", "exam_instances", "exam_template_questions", "exam_templates", "batch_memberships", "questions", "batches", "user_account"))
            jdbc.update("DELETE FROM learnium." + table + " WHERE centre_id IN (?, ?)", centre.getCentreId(), otherCentre.getCentreId());
        jdbc.update("DELETE FROM learnium.topics WHERE grade_subject_id = ?", pairing.getGradeSubjectId());
        jdbc.update("DELETE FROM learnium.grade_subjects WHERE grade_subject_id = ?", pairing.getGradeSubjectId());
        jdbc.update("DELETE FROM learnium.grades WHERE grade_id = ?", pairing.getGrade().getGradeId());
        jdbc.update("DELETE FROM learnium.subjects WHERE subject_id = ?", pairing.getSubject().getSubjectId());
        jdbc.update("DELETE FROM learnium.centres WHERE centre_id IN (?, ?)", centre.getCentreId(), otherCentre.getCentreId());
    }
    @Test void frozenMarkingJobStoresCriterionBreakdownAndStaysPending() throws Exception {
        UUID id = markingJobs.enqueueMarking(answer.getQuestionAttemptId());
        assertThat(markingJobs.enqueueMarking(answer.getQuestionAttemptId())).isEqualTo(id);
        markingProperties.setProvider("gemini"); markingProperties.setModel("changed-model");
        answer.setAnswerPayload(JSON.createObjectNode().put("text", "Later answer")); answers.saveAndFlush(answer);
        assertThat(generationClaims.claimNext()).isEmpty();
        assertThat(jobs.countByCentreCentreIdAndCreatedAtGreaterThanEqual(centre.getCentreId(), Instant.EPOCH)).isZero();
        assertThat(jobs.countByCentreCentreIdAndCreatedByAndCreatedAtGreaterThanEqual(centre.getCentreId(), student.getUserId(), Instant.EPOCH)).isZero();
        assertThat(worker.runOnce()).isTrue(); assertThat(worker.runOnce()).isFalse();
        var job = jobs.findById(id).orElseThrow(); assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.SUCCEEDED);
        assertThat(job.getAttempts()).isEqualTo(1); assertThat(job.getProvider()).isEqualTo("ollama");
        var stored = answers.findById(answer.getQuestionAttemptId()).orElseThrow();
        assertThat(stored.getMarkingStatus()).isEqualTo(MarkingStatus.PENDING);
        assertThat(stored.getAiSuggestedScore()).isEqualByComparingTo("2.50"); assertThat(stored.getConfirmedScore()).isNull();
        assertThat(stored.getAiBreakdown().path("criteria").fieldNames()).toIterable().containsExactly("crit-1", "crit-2", "crit-3");
        var capture = org.mockito.ArgumentCaptor.forClass(ModelTransport.ModelRequest.class); verify(transport).send(capture.capture());
        var request = JSON.readTree(capture.getValue().jsonBody());
        assertThat(request.path("model").asText()).isEqualTo("frozen-marking-model");
        assertThat(request.path("messages").get(1).path("content").asText()).contains("Two equal parts.").doesNotContain("Later answer");
    }
    @Test void malformedAndUnknownCriteriaFailWithoutPartialWrites() {
        UUID id = markingJobs.enqueueMarking(answer.getQuestionAttemptId());
        doReturn(new ModelTransport.ModelResponse(200, envelope(true))).when(transport).send(any());
        worker.runOnce(); var job = jobs.findById(id).orElseThrow();
        assertThat(job.getStatus()).isEqualTo(BackgroundJobStatus.FAILED); assertThat(job.getFailureKind()).isEqualTo(GenerationFailureKind.MALFORMED_RESPONSE);
        assertThat(job.getAttempts()).isEqualTo(1); assertThat(answers.findById(answer.getQuestionAttemptId()).orElseThrow().getAiBreakdown()).isNull();
        assertThat(answers.findById(answer.getQuestionAttemptId()).orElseThrow().getAiSuggestedScore()).isNull();
    }
    @Test void transientFailuresRetryWithBackoffAndStopAfterTwoAttempts() {
        UUID id = markingJobs.enqueueMarking(answer.getQuestionAttemptId());
        doThrow(new ModelTransportException(503)).when(transport).send(any());
        assertThat(worker.runOnce()).isTrue();
        var pending = jobs.findById(id).orElseThrow(); assertThat(pending.getStatus()).isEqualTo(BackgroundJobStatus.PENDING);
        assertThat(pending.getNextAttemptAt()).isAfter(Instant.now()); assertThat(worker.runOnce()).isFalse();
        pending.setNextAttemptAt(Instant.now().minusSeconds(10)); jobs.saveAndFlush(pending); worker.runOnce();
        var failed = jobs.findById(id).orElseThrow(); assertThat(failed.getAttempts()).isEqualTo(2); assertThat(failed.getStatus()).isEqualTo(BackgroundJobStatus.FAILED);
        assertThat(worker.runOnce()).isFalse(); verify(transport, times(2)).send(any());
    }
    @Test void expiredLeaseRejectsStaleCompletionsAndExhaustedClaims() {
        UUID id = markingJobs.enqueueMarking(answer.getQuestionAttemptId());
        var first = markingJobs.claimNext().orElseThrow();
        var job = jobs.findById(id).orElseThrow(); job.setLeaseExpiresAt(Instant.now().minusSeconds(5)); jobs.saveAndFlush(job);
        var second = markingJobs.claimNext().orElseThrow();
        var result = new MarkingService.Marked(new java.math.BigDecimal("1.00"), JSON.createObjectNode().put("feedback", "Feedback"));
        assertThat(markingJobs.succeed(first, result)).isFalse();
        assertThat(markingJobs.fail(first, GenerationFailureKind.MODEL_ERROR, true)).isFalse();
        assertThat(markingJobs.fail(first, GenerationFailureKind.MALFORMED_RESPONSE, false)).isFalse();
        job = jobs.findById(id).orElseThrow(); job.setLeaseExpiresAt(Instant.now().minusSeconds(5)); jobs.saveAndFlush(job);
        assertThat(markingJobs.claimNext()).isEmpty();
        assertThat(jobs.findById(id).orElseThrow().getAttempts()).isEqualTo(2);
        assertThat(markingJobs.succeed(second, result)).isFalse();
        assertThat(answers.findById(answer.getQuestionAttemptId()).orElseThrow().getAiSuggestedScore()).isNull();
    }
    @Test void lateAiResultDoesNotOverwriteTeacherConfirmation() {
        markingJobs.enqueueMarking(answer.getQuestionAttemptId()); var claim = markingJobs.claimNext().orElseThrow();
        answer.setMarkingStatus(MarkingStatus.CONFIRMED); answer.setConfirmedScore(new java.math.BigDecimal("2.00")); answers.saveAndFlush(answer);
        assertThat(markingJobs.succeed(claim, new MarkingService.Marked(java.math.BigDecimal.ONE, JSON.createObjectNode()))).isTrue();
        var confirmed = answers.findById(answer.getQuestionAttemptId()).orElseThrow();
        assertThat(confirmed.getConfirmedScore()).isEqualByComparingTo("2.00"); assertThat(confirmed.getAiBreakdown()).isNull(); assertThat(confirmed.getMarkingStatus()).isEqualTo(MarkingStatus.CONFIRMED);
    }
    @Test void rejectsMissingFrozenVersionsInvalidRubricsAndNonTextOrConfirmedAnswers() {
        assertThatThrownBy(() -> markingJobs.enqueueMarking(UUID.randomUUID())).isInstanceOf(com.learnium.common.error.BusinessException.class);
        answer.setQuestionVersion(99); answers.saveAndFlush(answer);
        assertThatThrownBy(() -> markingJobs.enqueueMarking(answer.getQuestionAttemptId())).hasMessageContaining("Frozen question");
        answer.setQuestionVersion(written.getVersion()); answers.saveAndFlush(answer);
        var snapshot = frozen.frozen(centre.getCentreId(), answer.getAttempt().getExamInstance().getExamInstanceId()).stream().filter(q -> q.getQuestion().getQuestionId().equals(written.getQuestionId())).findFirst().orElseThrow();
        var original = snapshot.getPayloadSnapshot().deepCopy(); snapshot.setPayloadSnapshot(JSON.createObjectNode()); frozen.saveAndFlush(snapshot);
        assertThatThrownBy(() -> markingJobs.enqueueMarking(answer.getQuestionAttemptId())).hasMessageContaining("valid frozen written");
        for (Object id : Arrays.asList(null, "", "crit-2", 42)) {
            var bad = original.deepCopy(); var criterion = (ObjectNode) bad.path("rubric").get(0);
            if (id == null) criterion.remove("id"); else criterion.set("id", JSON.valueToTree(id));
            snapshot.setPayloadSnapshot(bad); frozen.saveAndFlush(snapshot);
            assertThatThrownBy(() -> markingJobs.enqueueMarking(answer.getQuestionAttemptId())).hasMessageContaining("criterion IDs");
        }
        snapshot.setPayloadSnapshot(original); frozen.saveAndFlush(snapshot);
        for (var payload : List.of(JSON.createObjectNode(), JSON.createObjectNode().put("text", "x".repeat(20001)))) {
            answer.setAnswerPayload(payload); answers.saveAndFlush(answer);
            assertThatThrownBy(() -> markingJobs.enqueueMarking(answer.getQuestionAttemptId())).hasMessageContaining("unconfirmed text answer");
        }
        answer.setAnswerPayload(JSON.createObjectNode().put("text", "Answer")); answer.setMarkingStatus(MarkingStatus.CONFIRMED); answers.saveAndFlush(answer);
        assertThatThrownBy(() -> markingJobs.enqueueMarking(answer.getQuestionAttemptId())).hasMessageContaining("unconfirmed text answer");
        answer.setMarkingStatus(MarkingStatus.PENDING); answer.setQuestion(mcq); answer.setQuestionVersion(mcq.getVersion()); answers.saveAndFlush(answer);
        assertThatThrownBy(() -> markingJobs.enqueueMarking(answer.getQuestionAttemptId())).hasMessageContaining("valid frozen written");
    }
}
