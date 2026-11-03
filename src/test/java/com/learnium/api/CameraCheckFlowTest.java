package com.learnium.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.learnium.model.exam.CameraMode;
import com.learnium.model.exam.ProctorSnapshot;
import com.learnium.model.exam.SnapshotReason;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.exam.ProctorSnapshotRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class CameraCheckFlowTest extends PublishingFixtures {
    @Autowired ProctorSnapshotRepository snapshots;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 1, 2, 3, 4};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private UUID publishWithCamera(CameraMode mode) throws Exception {
        JsonNode created = create();
        JsonNode published = publish(created.path("id").asText(), publishBody());
        UUID instanceId = UUID.fromString(published.path("id").asText());
        instances.findById(instanceId).ifPresent(instance -> {
            instance.setCameraMode(mode);
            instances.saveAndFlush(instance);
        });
        return instanceId;
    }

    private String checkToken(UUID instanceId, byte[] frame) throws Exception {
        JsonNode response = json(mvc.perform(multipart("/api/v1/student/tests/" + instanceId + "/camera-check")
                        .file(new MockMultipartFile("file", "frame.jpg", "image/jpeg", frame)).with(as(student)))
                .andExpect(status().isOk()).andReturn());
        return response.path("checkToken").asText();
    }

    @Test
    void cameraOnStartRequiresALiveSingleUseCheck() throws Exception {
        UUID instanceId = publishWithCamera(CameraMode.ON);
        mvc.perform(post("/api/v1/student/tests/" + instanceId + "/attempts").with(as(student))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LRN-ATT-004"));
        mvc.perform(post("/api/v1/student/tests/" + instanceId + "/attempts").with(as(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cameraCheckToken\":\"bogus\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LRN-ATT-004"));

        String token = checkToken(instanceId, JPEG);
        assertThat(token).isNotBlank();
        ProctorSnapshot check = snapshots.findAll().get(0);
        assertThat(check.getReason()).isEqualTo(SnapshotReason.PRE_TEST);
        assertThat(check.getCheckTokenHash()).hasSize(64).doesNotContain(token);
        assertThat(check.getAttempt()).isNull();

        JsonNode started = json(mvc.perform(post("/api/v1/student/tests/" + instanceId + "/attempts").with(as(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cameraCheckToken\":\"" + token + "\"}"))
                .andExpect(status().isOk()).andReturn());
        UUID attemptId = UUID.fromString(started.path("attemptId").asText());
        ProctorSnapshot consumed = snapshots.findById(check.getSnapshotId()).orElseThrow();
        assertThat(consumed.getCheckUsedAt()).isNotNull();
        assertThat(consumed.getAttempt().getAttemptId()).isEqualTo(attemptId);

        // Resume returns the in-progress attempt without a new check:
        mvc.perform(post("/api/v1/student/tests/" + instanceId + "/attempts").with(as(student))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attemptId").value(attemptId.toString()));
        // The burned token cannot start a second attempt after this one is submitted:
        mvc.perform(post("/api/v1/student/attempts/" + attemptId + "/submit").with(as(student)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/student/tests/" + instanceId + "/attempts").with(as(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cameraCheckToken\":\"" + token + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LRN-ATT-004"));
    }

    @Test
    void expiredCheckIsRejected() throws Exception {
        UUID instanceId = publishWithCamera(CameraMode.ON);
        String token = checkToken(instanceId, PNG);
        ProctorSnapshot check = snapshots.findAll().get(0);
        check.setCheckExpiresAt(java.time.Instant.now().minusSeconds(60));
        snapshots.saveAndFlush(check);
        mvc.perform(post("/api/v1/student/tests/" + instanceId + "/attempts").with(as(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cameraCheckToken\":\"" + token + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LRN-ATT-004"));
    }

    @Test
    void cameraCheckValidatesAssignmentCameraSettingAndImage() throws Exception {
        UUID onInstance = publishWithCamera(CameraMode.ON);
        UUID offInstance = publishWithCamera(CameraMode.OFF);
        UserAccount unassigned = user(centre, UserRole.STUDENT);
        mvc.perform(multipart("/api/v1/student/tests/" + onInstance + "/camera-check")
                        .file(new MockMultipartFile("file", "frame.jpg", "image/jpeg", JPEG)).with(as(unassigned)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("LRN-ATT-001"));
        mvc.perform(multipart("/api/v1/student/tests/" + offInstance + "/camera-check")
                        .file(new MockMultipartFile("file", "frame.jpg", "image/jpeg", JPEG)).with(as(student)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LRN-VAL-001"));
        mvc.perform(multipart("/api/v1/student/tests/" + onInstance + "/camera-check")
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "not an image".getBytes()))
                        .with(as(student)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LRN-VAL-001"));
        byte[] oversized = new byte[2_000_001];
        System.arraycopy(JPEG, 0, oversized, 0, JPEG.length);
        mvc.perform(multipart("/api/v1/student/tests/" + onInstance + "/camera-check")
                        .file(new MockMultipartFile("file", "frame.jpg", "image/jpeg", oversized)).with(as(student)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LRN-VAL-001"));
        // Camera-OFF instances start with no token at all:
        mvc.perform(post("/api/v1/student/tests/" + offInstance + "/attempts").with(as(student)))
                .andExpect(status().isOk());
    }
}
