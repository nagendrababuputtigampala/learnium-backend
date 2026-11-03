package com.learnium.api;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.exam.StudentAttemptService;
import com.learnium.marking.MarkingProperties;
import com.learnium.model.exam.*;
import com.learnium.model.user.*;
import com.learnium.repository.content.BackgroundJobRepository;
import com.learnium.repository.exam.*;
import com.learnium.security.LearniumPrincipal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class StudentPlayerFlowTest extends PublishingFixtures {
    @MockitoBean Clock clock;
    @Autowired ExamAttemptRepository examAttempts;
    @Autowired QuestionAttemptRepository savedAnswers;
    @Autowired BackgroundJobRepository jobs;
    @Autowired MarkingProperties marking;
    @Autowired StudentAttemptService attemptService;
    Instant now;

    @BeforeEach void playerSetup() {
        now = Instant.parse("2026-11-03T18:00:00Z");
        org.mockito.Mockito.when(clock.instant()).thenReturn(now);
        marking.setProvider("ollama"); marking.setModel("student-marking");
    }

    @AfterEach void resetMarking() { marking.setProvider(""); marking.setModel(""); }

    @Test void startAuthorizationDeadlineResumeAndPreReleasePayloadSafety() throws Exception {
        JsonNode instance = scored("AFTER_CLOSE", 600, now.minusSeconds(60), now.plusSeconds(3600));
        UUID instanceId = UUID.fromString(instance.path("id").asText());

        mvc.perform(post("/api/v1/student/tests/" + instanceId + "/attempts").with(as(owner)))
                .andExpect(status().isForbidden());
        UserAccount unassigned = user(centre, UserRole.STUDENT);
        mvc.perform(post("/api/v1/student/tests/" + instanceId + "/attempts").with(as(unassigned)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LRN-ATT-001"));

        JsonNode started = start(instanceId, student);
        assertThat(started.path("deadlineAt").asText()).isEqualTo(now.plusSeconds(600).toString());
        assertThat(started.path("remainingSeconds").asLong()).isEqualTo(600);
        assertPayloadSafe(started);
        JsonNode resumed = start(instanceId, student);
        assertThat(resumed.path("attemptId").asText()).isEqualTo(started.path("attemptId").asText());
        assertThat(resumed.path("attemptNo").asInt()).isEqualTo(1);
        assertPayloadSafe(resumed);
        UUID mcqItem = itemIds(started).get("MCQ");
        save(UUID.fromString(started.path("attemptId").asText()), mcqItem,
                JSON.createObjectNode().put("optionId", mcq.getCorrectRef()));
        JsonNode loaded = json(mvc.perform(get("/api/v1/student/attempts/" + started.path("attemptId").asText())
                .with(as(student))).andExpect(status().isOk()).andReturn());
        assertPayloadSafe(loaded);
        assertThat(loaded.path("answers")).hasSize(1);
    }

    @Test void startRejectsWindowAndComputesCloseOnlyDeadline() throws Exception {
        JsonNode future = scored("NEVER", null, now.plusSeconds(60), now.plusSeconds(3600));
        mvc.perform(post("/api/v1/student/tests/" + future.path("id").asText() + "/attempts").with(as(student)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LRN-ATT-002"));
        JsonNode open = scored("NEVER", null, now.minusSeconds(60), now.plusSeconds(900));
        assertThat(start(UUID.fromString(open.path("id").asText()), student).path("deadlineAt").asText())
                .isEqualTo(now.plusSeconds(900).toString());
    }

    @Test void typedAutosaveGradesBoundariesSubmitsIdempotentlyAndRetakes() throws Exception {
        JsonNode instance = scored("ON_SUBMIT", null, now.minusSeconds(60), now.plusSeconds(3600));
        UUID instanceId = UUID.fromString(instance.path("id").asText());
        JsonNode started = start(instanceId, student); UUID attemptId = UUID.fromString(started.path("attemptId").asText());
        Map<String, UUID> itemIds = itemIds(started);
        save(attemptId, itemIds.get("MCQ"), JSON.createObjectNode().put("optionId", mcq.getCorrectRef()));
        save(attemptId, itemIds.get("FILL_BLANK"), JSON.createObjectNode().put("text", "  fIfTy "));
        save(attemptId, itemIds.get("NUMERIC"), JSON.createObjectNode().put("number", 2.1));
        save(attemptId, itemIds.get("WRITTEN"), JSON.createObjectNode().put("text", "Two equal pieces."));
        save(attemptId, itemIds.get("WRITTEN"), JSON.createObjectNode().put("text", "Two equal parts."));
        assertThat(savedAnswers.findByCentreIdAndAttemptId(centre.getCentreId(), attemptId)).hasSize(4);

        JsonNode result = submit(attemptId);
        assertThat(result.path("score").decimalValue()).isEqualByComparingTo("4.50");
        assertThat(result.path("markingPending").asBoolean()).isTrue();
        assertThat(result.path("answersReleased").asBoolean()).isTrue();
        assertThat(result.path("items").toString()).contains("CORRECT", "PENDING_MARKING", "workedSolution", "modelAnswer");
        assertThat(submit(attemptId)).isEqualTo(result);
        long writtenJobs = jobs.findAll().stream().filter(job -> "MARKING".equals(job.getJobType())
                && job.getQuestionAttemptId() != null
                && savedAnswers.findById(job.getQuestionAttemptId()).map(a -> a.getAttempt().getAttemptId().equals(attemptId)).orElse(false)).count();
        assertThat(writtenJobs).isEqualTo(1);

        JsonNode second = start(instanceId, student);
        assertThat(second.path("attemptNo").asInt()).isEqualTo(2);
        submit(UUID.fromString(second.path("attemptId").asText()));
        mvc.perform(post("/api/v1/student/tests/" + instanceId + "/attempts").with(as(student)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LRN-ATT-003"));
    }

    @Test void autosaveRejectsWrongTypesForeignItemsAndExpiredAttempts() throws Exception {
        JsonNode instance = scored("NEVER", null, now.minusSeconds(60), now.plusSeconds(60));
        JsonNode started = start(UUID.fromString(instance.path("id").asText()), student);
        UUID attemptId = UUID.fromString(started.path("attemptId").asText()); Map<String, UUID> ids = itemIds(started);
        mvc.perform(put("/api/v1/student/attempts/" + attemptId + "/answers/" + ids.get("NUMERIC")).with(as(student))
                .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":{\"text\":\"no\"}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/student/attempts/" + attemptId + "/answers/" + UUID.randomUUID()).with(as(student))
                .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LRN-ATT-001"));
        org.mockito.Mockito.when(clock.instant()).thenReturn(now.plusSeconds(60));
        mvc.perform(put("/api/v1/student/attempts/" + attemptId + "/answers/" + ids.get("MCQ")).with(as(student))
                .contentType(MediaType.APPLICATION_JSON).content("{\"answer\":{\"optionId\":\"a\"}}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LRN-ATT-002"));
    }

    @Test void resultHonoursNeverAndAfterClosePoliciesAndConfirmedWrittenScores() throws Exception {
        JsonNode never = scored("NEVER", null, now.minusSeconds(60), now.plusSeconds(3600));
        JsonNode attempt = start(UUID.fromString(never.path("id").asText()), student);
        JsonNode neverResult = submit(UUID.fromString(attempt.path("attemptId").asText()));
        assertThat(neverResult.path("answersReleased").asBoolean()).isFalse();
        assertThat(neverResult.path("items").toString()).doesNotContain("correctRef", "workedSolution", "modelAnswer");

        JsonNode after = scored("AFTER_CLOSE", null, now.minusSeconds(60), now.plusSeconds(3600));
        JsonNode afterAttempt = start(UUID.fromString(after.path("id").asText()), student);
        UUID afterId = UUID.fromString(afterAttempt.path("attemptId").asText());
        JsonNode beforeClose = submit(afterId); assertThat(beforeClose.path("answersReleased").asBoolean()).isFalse();
        ExamAttempt persisted = examAttempts.findById(afterId).orElseThrow();
        persisted.getExamInstance().setClosesAt(now.minusSeconds(1)); instances.saveAndFlush(persisted.getExamInstance());
        JsonNode released = json(mvc.perform(get("/api/v1/student/attempts/" + afterId + "/result").with(as(student)))
                .andExpect(status().isOk()).andReturn());
        assertThat(released.path("answersReleased").asBoolean()).isTrue();
        QuestionAttempt writtenAnswer = savedAnswers.findByCentreIdAndAttemptId(centre.getCentreId(), afterId).stream()
                .filter(answer -> answer.getExamInstanceQuestion().getQuestion().getQuestionType().getCode().equals("WRITTEN"))
                .findFirst().orElseThrow();
        writtenAnswer.setMarkingStatus(MarkingStatus.CONFIRMED); writtenAnswer.setConfirmedScore(new java.math.BigDecimal("2.25"));
        writtenAnswer.setMarkerFeedback("Well structured"); savedAnswers.saveAndFlush(writtenAnswer);
        JsonNode confirmed = json(mvc.perform(get("/api/v1/student/attempts/" + afterId + "/result").with(as(student)))
                .andExpect(status().isOk()).andReturn());
        assertThat(confirmed.path("score").decimalValue()).isEqualByComparingTo("2.25");
        assertThat(confirmed.path("items").toString()).contains("CONFIRMED", "Well structured");
    }

    @Test void sweeperAndManualSubmitRaceIsIdempotentWithOneWrittenAnswer() throws Exception {
        JsonNode instance = scored("NEVER", null, now.minusSeconds(60), now.plusSeconds(60));
        JsonNode started = start(UUID.fromString(instance.path("id").asText()), student);
        UUID id = UUID.fromString(started.path("attemptId").asText());
        var automatic = attemptService.autoSubmit(id);
        var manual = attemptService.submit(LearniumPrincipal.student(student.getUserId(), centre.getCentreId()), id);
        assertThat(automatic.status()).isEqualTo("AUTO_SUBMITTED");
        assertThat(manual.status()).isEqualTo("AUTO_SUBMITTED");
        assertThat(savedAnswers.findByCentreIdAndAttemptId(centre.getCentreId(), id).stream()
                .filter(answer -> answer.getExamInstanceQuestion().getQuestion().getQuestionType().getCode().equals("WRITTEN"))).hasSize(1);
    }

    @Test void flashcardPracticeReturnsFrozenCardsWithoutAttempts() throws Exception {
        Map<String,Object> definition = new HashMap<>(Map.of("name", "Fraction cards", "gradeSubjectId",
                pairing.getGradeSubjectId(), "kind", "FLASHCARD_PRACTICE", "items", List.of(item(flashcard, 0))));
        JsonNode created = create(definition, owner); Map<String,Object> publish = publishWindow(now.minusSeconds(60), now.plusSeconds(600));
        JsonNode instance = publish(created.path("id").asText(), publish); UUID id = UUID.fromString(instance.path("id").asText());
        JsonNode practice = json(mvc.perform(get("/api/v1/student/tests/" + id + "/practice").with(as(student)))
                .andExpect(status().isOk()).andReturn());
        assertThat(practice.path("cards").toString()).contains("Half", "Two quarters");
        assertThat(examAttempts.findByCentreIdAndExamInstanceId(centre.getCentreId(), id)).isEmpty();
    }

    private JsonNode scored(String policy, Integer limit, Instant opens, Instant closes) throws Exception {
        Map<String,Object> definition = new HashMap<>(Map.of("name", "Player test", "gradeSubjectId", pairing.getGradeSubjectId(),
                "kind", "SCORED_TEST", "items", List.of(item(mcq, 2), item(fillBlank, 1), item(numeric, 1.5), item(written, 3))));
        JsonNode created = create(definition, owner); Map<String,Object> publish = publishWindow(opens, closes);
        publish.put("answerReleasePolicy", policy); publish.put("maxAttempts", 2); if (limit != null) publish.put("timeLimitSeconds", limit);
        return publish(created.path("id").asText(), publish);
    }
    private Map<String,Object> publishWindow(Instant opens, Instant closes) {
        Map<String,Object> value = new HashMap<>(); value.put("batchId", batch.getBatchId());
        value.put("opensAt", opens.toString()); value.put("closesAt", closes.toString()); return value;
    }
    private JsonNode start(UUID instanceId, UserAccount actor) throws Exception {
        return json(mvc.perform(post("/api/v1/student/tests/" + instanceId + "/attempts").with(as(actor)))
                .andExpect(status().isOk()).andReturn());
    }
    private JsonNode submit(UUID attemptId) throws Exception {
        return json(mvc.perform(post("/api/v1/student/attempts/" + attemptId + "/submit").with(as(student)))
                .andExpect(status().isOk()).andReturn());
    }
    private void save(UUID attempt, UUID item, JsonNode answer) throws Exception {
        mvc.perform(put("/api/v1/student/attempts/" + attempt + "/answers/" + item).with(as(student))
                .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("answer", answer))))
                .andExpect(status().isOk());
    }
    private Map<String,UUID> itemIds(JsonNode attempt) {
        Map<String,UUID> out = new HashMap<>(); attempt.path("items").forEach(item -> out.put(item.path("type").asText(), UUID.fromString(item.path("itemId").asText()))); return out;
    }
    private void assertPayloadSafe(JsonNode response) {
        response.path("items").forEach(item -> {
            JsonNode payload = item.path("payload");
            assertThat(payload.has("correctRef")).isFalse(); assertThat(payload.has("answer")).isFalse();
            assertThat(payload.has("answerValue")).isFalse(); assertThat(payload.has("tolerance")).isFalse();
            assertThat(payload.has("workedSolution")).isFalse(); assertThat(payload.has("modelAnswer")).isFalse();
            assertThat(payload.has("rubric")).isFalse(); assertThat(payload.has("correctIndex")).isFalse();
        });
    }
}
