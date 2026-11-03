package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.model.exam.ExamAttemptStatus;
import com.learnium.repository.exam.ExamAttemptRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class AttemptAdminFlowTest extends PublishingFixtures {
    @Autowired ExamAttemptRepository examAttempts;

    private record Scenario(UUID instanceId, UUID attemptId, UUID writtenAnswerId) {}

    private Scenario submittedWithFlags() throws Exception {
        JsonNode created = create();
        JsonNode published = publish(created.path("id").asText(), publishBody());
        UUID instanceId = UUID.fromString(published.path("id").asText());
        instances.findById(instanceId).ifPresent(instance -> {
            instance.setIntegritySignalsOn(true);
            instances.saveAndFlush(instance);
        });
        JsonNode started = json(mvc.perform(post("/api/v1/student/tests/" + instanceId + "/attempts").with(as(student)))
                .andExpect(status().isOk()).andReturn());
        UUID attemptId = UUID.fromString(started.path("attemptId").asText());
        UUID mcqItem = null;
        UUID writtenItem = null;
        for (JsonNode item : started.path("items")) {
            if ("MCQ".equals(item.path("type").asText())) mcqItem = UUID.fromString(item.path("itemId").asText());
            if ("WRITTEN".equals(item.path("type").asText())) writtenItem = UUID.fromString(item.path("itemId").asText());
        }
        mvc.perform(put("/api/v1/student/attempts/" + attemptId + "/answers/" + mcqItem).with(as(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":{\"optionId\":\"" + mcq.getCorrectRef() + "\"}}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/student/attempts/" + attemptId + "/answers/" + writtenItem).with(as(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answer\":{\"text\":\"Two equal parts.\"}}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/student/attempts/" + attemptId + "/integrity-events").with(as(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("events", List.of(
                                Map.of("type", "TAB_HIDDEN", "occurredAt", "2026-11-03T18:01:00Z"),
                                Map.of("type", "TAB_HIDDEN", "occurredAt", "2026-11-03T18:03:00Z"),
                                Map.of("type", "FOCUS_LOST", "occurredAt", "2026-11-03T18:02:00Z"))))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recorded").value(3));
        mvc.perform(post("/api/v1/student/attempts/" + attemptId + "/submit").with(as(student)))
                .andExpect(status().isOk());
        JsonNode queue = json(mvc.perform(get("/api/v1/marking-queue").with(as(owner)))
                .andExpect(status().isOk()).andReturn());
        UUID writtenAnswerId = null;
        for (JsonNode group : queue) {
            for (JsonNode item : group.path("items")) {
                writtenAnswerId = UUID.fromString(item.path("questionAttemptId").asText());
            }
        }
        assertThat(writtenAnswerId).isNotNull();
        return new Scenario(instanceId, attemptId, writtenAnswerId);
    }

    @Test
    void attemptListShowsScoreStateAndFlagCountsForManagingStaffOnly() throws Exception {
        Scenario s = submittedWithFlags();
        JsonNode list = json(mvc.perform(get("/api/v1/instances/" + s.instanceId() + "/attempts").with(as(owner)))
                .andExpect(status().isOk()).andReturn());
        assertThat(list).hasSize(1);
        JsonNode row = list.get(0);
        assertThat(row.path("attemptId").asText()).isEqualTo(s.attemptId().toString());
        assertThat(row.path("status").asText()).isEqualTo("SUBMITTED");
        assertThat(row.path("score").decimalValue()).isEqualByComparingTo("2.50");
        assertThat(row.path("maxScore").decimalValue()).isEqualByComparingTo("3.25");
        assertThat(row.path("markingPending").asBoolean()).isTrue();
        assertThat(row.path("flags").path("TAB_HIDDEN").asLong()).isEqualTo(2);
        assertThat(row.path("flags").path("FOCUS_LOST").asLong()).isEqualTo(1);
        assertThat(row.path("flagTotal").asLong()).isEqualTo(3);
        assertThat(row.path("username").asText()).isEqualTo(student.getUsername());

        mvc.perform(get("/api/v1/instances/" + s.instanceId() + "/attempts").with(as(admin)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/instances/" + s.instanceId() + "/attempts").with(as(colleague)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LRN-TST-004"));
        mvc.perform(get("/api/v1/instances/" + s.instanceId() + "/attempts").with(as(foreign)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/instances/" + s.instanceId() + "/attempts").with(as(student)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/instances/" + UUID.randomUUID() + "/attempts").with(as(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void timelineIsOrderedAndScoped() throws Exception {
        Scenario s = submittedWithFlags();
        JsonNode timeline = json(mvc.perform(get("/api/v1/attempts/" + s.attemptId() + "/integrity-events").with(as(owner)))
                .andExpect(status().isOk()).andReturn());
        assertThat(timeline).hasSize(3);
        assertThat(timeline.get(0).path("type").asText()).isEqualTo("TAB_HIDDEN");
        assertThat(timeline.get(1).path("type").asText()).isEqualTo("FOCUS_LOST");
        assertThat(timeline.get(2).path("type").asText()).isEqualTo("TAB_HIDDEN");
        mvc.perform(get("/api/v1/attempts/" + s.attemptId() + "/integrity-events").with(as(colleague)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/attempts/" + UUID.randomUUID() + "/integrity-events").with(as(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidateRemovesAttemptFromQueueResultsAndIsIdempotent() throws Exception {
        Scenario s = submittedWithFlags();
        JsonNode invalidated = json(mvc.perform(post("/api/v1/attempts/" + s.attemptId() + "/invalidate").with(as(owner)))
                .andExpect(status().isOk()).andReturn());
        assertThat(invalidated.path("status").asText()).isEqualTo("INVALIDATED");
        assertThat(examAttempts.findById(s.attemptId()).orElseThrow().getStatus())
                .isEqualTo(ExamAttemptStatus.INVALIDATED);

        JsonNode queue = json(mvc.perform(get("/api/v1/marking-queue").with(as(owner)))
                .andExpect(status().isOk()).andReturn());
        assertThat(queue.toString()).doesNotContain(s.writtenAnswerId().toString());
        mvc.perform(get("/api/v1/marking-queue/" + s.writtenAnswerId()).with(as(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/student/attempts/" + s.attemptId() + "/result").with(as(student)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LRN-ATT-001"));

        mvc.perform(post("/api/v1/attempts/" + s.attemptId() + "/invalidate").with(as(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INVALIDATED"));
        mvc.perform(post("/api/v1/attempts/" + s.attemptId() + "/invalidate").with(as(colleague)))
                .andExpect(status().isNotFound());
    }
}
