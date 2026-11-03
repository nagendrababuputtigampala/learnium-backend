package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.model.exam.ExamAttempt;
import com.learnium.model.exam.ExamAttemptStatus;
import com.learnium.model.exam.IntegrityEventType;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.exam.ExamAttemptRepository;
import com.learnium.repository.exam.IntegrityEventRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class IntegrityFlowTest extends PublishingFixtures {
    @Autowired IntegrityEventRepository events;
    @Autowired ExamAttemptRepository examAttempts;

    private UUID startAttempt(boolean signalsOn) throws Exception {
        JsonNode created = create();
        JsonNode published = publish(created.path("id").asText(), publishBody());
        UUID instanceId = UUID.fromString(published.path("id").asText());
        instances.findById(instanceId).ifPresent(instance -> {
            instance.setIntegritySignalsOn(signalsOn);
            instances.saveAndFlush(instance);
        });
        JsonNode started = json(mvc.perform(post("/api/v1/student/tests/" + instanceId + "/attempts").with(as(student)))
                .andExpect(status().isOk()).andReturn());
        return UUID.fromString(started.path("attemptId").asText());
    }

    private String batch(Object... events) throws Exception {
        return body(Map.of("events", List.of(events)));
    }

    private Map<String, Object> event(String type, String occurredAt) {
        return occurredAt == null ? Map.of("type", type) : Map.of("type", type, "occurredAt", occurredAt);
    }

    @Test
    void recordsEventsWhenSignalsOn() throws Exception {
        UUID attemptId = startAttempt(true);
        String response = mvc.perform(post("/api/v1/student/attempts/" + attemptId + "/integrity-events").with(as(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch(
                                event("TAB_HIDDEN", "2026-11-03T18:01:00Z"),
                                event("CAMERA_LOST", "2026-11-03T18:02:00Z"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JSON.readTree(response).path("recorded").asInt()).isEqualTo(2);
        var rows = events.findByCentreIdAndAttemptAttemptIdOrderByOccurredAtAsc(centre.getCentreId(), attemptId);
        assertThat(rows).extracting(row -> row.getEventType())
                .containsExactly(IntegrityEventType.TAB_HIDDEN, IntegrityEventType.CAMERA_LOST);
    }

    @Test
    void dropsSilentlyWhenSignalsOffOrAttemptInvalidated() throws Exception {
        UUID offAttempt = startAttempt(false);
        mvc.perform(post("/api/v1/student/attempts/" + offAttempt + "/integrity-events").with(as(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch(event("FOCUS_LOST", "2026-11-03T18:01:00Z"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recorded").value(0));
        assertThat(events.findAll()).isEmpty();

        UUID invalidated = startAttempt(true);
        ExamAttempt attempt = examAttempts.findById(invalidated).orElseThrow();
        attempt.setStatus(ExamAttemptStatus.INVALIDATED);
        examAttempts.saveAndFlush(attempt);
        mvc.perform(post("/api/v1/student/attempts/" + invalidated + "/integrity-events").with(as(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch(event("FOCUS_LOST", "2026-11-03T18:01:00Z"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recorded").value(0));
        assertThat(events.findAll()).isEmpty();
    }

    @Test
    void rejectsForeignAttemptsAndStaffCallers() throws Exception {
        UUID attemptId = startAttempt(true);
        UserAccount otherStudent = user(centre, UserRole.STUDENT);
        mvc.perform(post("/api/v1/student/attempts/" + attemptId + "/integrity-events").with(as(otherStudent))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch(event("TAB_HIDDEN", "2026-11-03T18:01:00Z"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LRN-ATT-001"));
        mvc.perform(post("/api/v1/student/attempts/" + UUID.randomUUID() + "/integrity-events").with(as(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch(event("TAB_HIDDEN", "2026-11-03T18:01:00Z"))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LRN-ATT-001"));
        mvc.perform(post("/api/v1/student/attempts/" + attemptId + "/integrity-events").with(as(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch(event("TAB_HIDDEN", "2026-11-03T18:01:00Z"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void validatesBatchShapeTypesAndTimestamps() throws Exception {
        UUID attemptId = startAttempt(true);
        var tooMany = new java.util.ArrayList<>();
        for (int i = 0; i < 21; i++) tooMany.add(event("TAB_HIDDEN", "2026-11-03T18:01:00Z"));
        mvc.perform(post("/api/v1/student/attempts/" + attemptId + "/integrity-events").with(as(student))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("events", tooMany))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LRN-VAL-001"));
        mvc.perform(post("/api/v1/student/attempts/" + attemptId + "/integrity-events").with(as(student))
                        .contentType(MediaType.APPLICATION_JSON).content(body(Map.of("events", List.of()))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LRN-VAL-001"));
        mvc.perform(post("/api/v1/student/attempts/" + attemptId + "/integrity-events").with(as(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch(event("SNEAKED_OUT", "2026-11-03T18:01:00Z"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LRN-VAL-001"));
        mvc.perform(post("/api/v1/student/attempts/" + attemptId + "/integrity-events").with(as(student))
                        .contentType(MediaType.APPLICATION_JSON).content(batch(event("TAB_HIDDEN", null))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LRN-VAL-001"));
        assertThat(events.findAll()).isEmpty();
    }
}
