package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.exam.ProctorService;
import com.learnium.model.exam.CameraMode;
import com.learnium.model.exam.ProctorSnapshot;
import com.learnium.model.exam.SnapshotReason;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.exam.ProctorSnapshotRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SnapshotFlowTest extends PublishingFixtures {
    @Autowired ProctorSnapshotRepository snapshots;
    @Autowired ProctorService proctor;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 9, 9, 9};

    private UUID cameraAttempt() throws Exception {
        JsonNode created = create();
        JsonNode published = publish(created.path("id").asText(), publishBody());
        UUID instanceId = UUID.fromString(published.path("id").asText());
        instances.findById(instanceId).ifPresent(instance -> {
            instance.setCameraMode(CameraMode.ON);
            instances.saveAndFlush(instance);
        });
        JsonNode check = json(mvc.perform(multipart("/api/v1/student/tests/" + instanceId + "/camera-check")
                        .file(new MockMultipartFile("file", "frame.jpg", "image/jpeg", JPEG)).with(as(student)))
                .andExpect(status().isOk()).andReturn());
        JsonNode started = json(mvc.perform(post("/api/v1/student/tests/" + instanceId + "/attempts").with(as(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cameraCheckToken\":\"" + check.path("checkToken").asText() + "\"}"))
                .andExpect(status().isOk()).andReturn());
        return UUID.fromString(started.path("attemptId").asText());
    }

    private MockMultipartHttpServletRequestBuilder upload(UUID attemptId, String reason, byte[] frame) {
        return multipart("/api/v1/student/attempts/" + attemptId + "/snapshots")
                .file(new MockMultipartFile("file", "frame.jpg", "image/jpeg", frame))
                .param("reason", reason);
    }

    @Test
    void uploadListAndViewWithAuditScope() throws Exception {
        UUID attemptId = cameraAttempt();
        mvc.perform(upload(attemptId, "PERIODIC", JPEG).with(as(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("PERIODIC"));
        mvc.perform(upload(attemptId, "EVENT", JPEG).with(as(student)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reason").value("EVENT"));
        mvc.perform(upload(attemptId, "PRE_TEST", JPEG).with(as(student)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LRN-VAL-001"));
        mvc.perform(upload(attemptId, "WHENEVER", JPEG).with(as(student)))
                .andExpect(status().isBadRequest());
        mvc.perform(upload(attemptId, "PERIODIC", "plain text".getBytes()).with(as(student)))
                .andExpect(status().isBadRequest());
        UserAccount otherStudent = user(centre, UserRole.STUDENT);
        mvc.perform(upload(attemptId, "PERIODIC", JPEG).with(as(otherStudent)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LRN-ATT-001"));

        JsonNode list = json(mvc.perform(get("/api/v1/attempts/" + attemptId + "/snapshots").with(as(owner)))
                .andExpect(status().isOk()).andReturn());
        assertThat(list).hasSize(3);
        assertThat(list.toString()).contains("PRE_TEST", "PERIODIC", "EVENT");
        UUID periodicId = null;
        for (JsonNode row : list) {
            if ("PERIODIC".equals(row.path("reason").asText())) periodicId = UUID.fromString(row.path("snapshotId").asText());
        }
        byte[] viewed = mvc.perform(get("/api/v1/attempts/" + attemptId + "/snapshots/" + periodicId).with(as(owner)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(viewed).isEqualTo(JPEG);
        mvc.perform(get("/api/v1/attempts/" + attemptId + "/snapshots/" + periodicId).with(as(colleague)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/attempts/" + UUID.randomUUID() + "/snapshots/" + periodicId).with(as(owner)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/attempts/" + attemptId + "/snapshots").with(as(student)))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/student/attempts/" + attemptId + "/submit").with(as(student)))
                .andExpect(status().isOk());
        mvc.perform(upload(attemptId, "PERIODIC", JPEG).with(as(student)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LRN-ATT-002"));
    }

    @Test
    void retentionPurgesOnlyExpiredSnapshots() throws Exception {
        UUID attemptId = cameraAttempt();
        var attempt = snapshots.findAll().get(0).getAttempt();
        ProctorSnapshot expired = new ProctorSnapshot();
        expired.setCentreId(centre.getCentreId());
        expired.setAttempt(attempt);
        expired.setExamInstance(attempt.getExamInstance());
        expired.setUser(student);
        expired.setStorageKey("proctor/expired-frame.jpg");
        expired.setReason(SnapshotReason.PERIODIC);
        expired.setCapturedAt(Instant.now().minusSeconds(31L * 24 * 3600));
        snapshots.saveAndFlush(expired);

        int purged = proctor.purgeExpired();
        assertThat(purged).isEqualTo(1);
        assertThat(snapshots.findById(expired.getSnapshotId())).isEmpty();
        assertThat(snapshots.findByCentreIdAndAttemptAttemptIdOrderByCapturedAtAsc(centre.getCentreId(), attemptId))
                .hasSize(1);
    }
}
