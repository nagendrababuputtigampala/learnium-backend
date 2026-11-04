package com.learnium.exam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.exam.CameraMode;
import com.learnium.model.exam.ExamAttempt;
import com.learnium.model.exam.ExamAttemptStatus;
import com.learnium.model.exam.ExamInstance;
import com.learnium.model.exam.ExamInstanceStudent;
import com.learnium.model.exam.ProctorSnapshot;
import com.learnium.model.exam.SnapshotReason;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.exam.ExamAttemptRepository;
import com.learnium.repository.exam.ExamInstanceRepository;
import com.learnium.repository.exam.ExamInstanceStudentRepository;
import com.learnium.repository.exam.ProctorSnapshotRepository;
import com.learnium.security.LearniumPrincipal;
import com.learnium.security.TokenHashing;
import com.learnium.storage.StorageService;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class ProctorServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-03T18:00:00Z");
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x10, 0x20};
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D};
    private static final byte[] WEBP = {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50};

    private final ExamInstanceStudentRepository assignments = mock(ExamInstanceStudentRepository.class);
    private final ExamInstanceRepository instances = mock(ExamInstanceRepository.class);
    private final ExamAttemptRepository examAttempts = mock(ExamAttemptRepository.class);
    private final ProctorSnapshotRepository snapshots = mock(ProctorSnapshotRepository.class);
    private final StorageService storage = mock(StorageService.class);
    private final ProctorService service =
            new ProctorService(assignments, instances, examAttempts, snapshots, storage, Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID centreId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final LearniumPrincipal caller = new LearniumPrincipal(userId, centreId, UserRole.STUDENT);

    private ExamInstance instance(CameraMode mode) {
        ExamInstance instance = mock(ExamInstance.class);
        UUID id = UUID.randomUUID();
        when(instance.getExamInstanceId()).thenReturn(id);
        when(instance.getCentreId()).thenReturn(centreId);
        when(instance.getCameraMode()).thenReturn(mode);
        return instance;
    }

    private UserAccount user(UUID id) {
        UserAccount user = mock(UserAccount.class);
        when(user.getUserId()).thenReturn(id);
        return user;
    }

    private ExamAttempt attempt(ExamInstance instance, ExamAttemptStatus status, UserAccount owner) {
        ExamAttempt attempt = mock(ExamAttempt.class);
        when(attempt.getCentreId()).thenReturn(centreId);
        when(attempt.getUser()).thenReturn(owner);
        when(attempt.getExamInstance()).thenReturn(instance);
        when(attempt.getStatus()).thenReturn(status);
        return attempt;
    }

    private ProctorSnapshot checkSnapshot(ExamInstance instance, UserAccount user) {
        ProctorSnapshot snapshot = new ProctorSnapshot();
        snapshot.setCentreId(centreId);
        snapshot.setExamInstance(instance);
        snapshot.setUser(user);
        snapshot.setReason(SnapshotReason.PRE_TEST);
        snapshot.setCheckExpiresAt(NOW.plusSeconds(600));
        snapshot.setStorageKey("key.jpg");
        return snapshot;
    }

    @Test
    void cameraOnModes() {
        ExamInstance on = instance(CameraMode.ON);
        ExamInstance verify = instance(CameraMode.VERIFY_SNAPSHOTS);
        ExamInstance off = instance(CameraMode.OFF);
        assertThat(ProctorService.cameraOn(on)).isTrue();
        assertThat(ProctorService.cameraOn(verify)).isTrue();
        assertThat(ProctorService.cameraOn(off)).isFalse();
    }

    @Test
    void imageBytesValidation() throws Exception {
        assertThatThrownBy(() -> ProctorService.imageBytes(null)).isInstanceOf(BusinessException.class);
        MockMultipartFile empty = new MockMultipartFile("file", new byte[0]);
        assertThatThrownBy(() -> ProctorService.imageBytes(empty)).isInstanceOf(BusinessException.class);
        MultipartFile big = mock(MultipartFile.class);
        when(big.isEmpty()).thenReturn(false);
        when(big.getSize()).thenReturn(2_000_001L);
        assertThatThrownBy(() -> ProctorService.imageBytes(big)).isInstanceOf(BusinessException.class);
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.isEmpty()).thenReturn(false);
        when(broken.getSize()).thenReturn(10L);
        when(broken.getBytes()).thenThrow(new IOException("nope"));
        assertThatThrownBy(() -> ProctorService.imageBytes(broken)).isInstanceOf(BusinessException.class);
        MockMultipartFile ok = new MockMultipartFile("file", JPEG);
        assertThat(ProctorService.imageBytes(ok)).isEqualTo(JPEG);
    }

    @Test
    void imageExtensionDetection() {
        assertThat(ProctorService.imageExtension(JPEG)).isEqualTo(".jpg");
        assertThat(ProctorService.imageExtension(PNG)).isEqualTo(".png");
        assertThat(ProctorService.imageExtension(WEBP)).isEqualTo(".webp");
        byte[] tiny = {0x52, 0x49};
        assertThatThrownBy(() -> ProctorService.imageExtension(tiny)).isInstanceOf(BusinessException.class);
        byte[] riffNotWebp = {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x41, 0x41, 0x41, 0x41};
        assertThatThrownBy(() -> ProctorService.imageExtension(riffNotWebp)).isInstanceOf(BusinessException.class);
        byte[] text = "plain text".getBytes();
        assertThatThrownBy(() -> ProctorService.imageExtension(text)).isInstanceOf(BusinessException.class);
    }

    @Test
    void cameraCheckRejectsUnassignedForeignAndCameraOff() {
        UUID instanceId = UUID.randomUUID();
        MockMultipartFile frame = new MockMultipartFile("file", JPEG);
        when(assignments.findAssigned(centreId, instanceId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cameraCheck(caller, instanceId, frame))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ATT_NOT_ASSIGNED);

        ExamInstanceStudent assignment = mock(ExamInstanceStudent.class);
        UserAccount me = user(userId);
        when(assignment.getUser()).thenReturn(me);
        when(assignments.findAssigned(centreId, instanceId, userId)).thenReturn(Optional.of(assignment));
        when(instances.findById(instanceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cameraCheck(caller, instanceId, frame))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ATT_NOT_ASSIGNED);

        ExamInstance off = instance(CameraMode.OFF);
        when(instances.findById(instanceId)).thenReturn(Optional.of(off));
        assertThatThrownBy(() -> service.cameraCheck(caller, instanceId, frame))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getMessage())
                .isEqualTo("Camera is not enabled for this test");
    }

    @Test
    void cameraCheckStoresHashedToken() {
        UUID instanceId = UUID.randomUUID();
        ExamInstanceStudent assignment = mock(ExamInstanceStudent.class);
        UserAccount me = user(userId);
        when(assignment.getUser()).thenReturn(me);
        when(assignments.findAssigned(centreId, instanceId, userId)).thenReturn(Optional.of(assignment));
        ExamInstance verifyMode = instance(CameraMode.VERIFY_SNAPSHOTS);
        when(instances.findById(instanceId)).thenReturn(Optional.of(verifyMode));
        when(storage.store(any(), any(), any(), any())).thenReturn("stored.jpg");
        MockMultipartFile frame = new MockMultipartFile("file", "frame.jpg", "image/jpeg", JPEG);
        var dto = service.cameraCheck(caller, instanceId, frame);
        assertThat(dto.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        verify(snapshots).save(any(ProctorSnapshot.class));
    }

    @Test
    void requireCheckFiltersEveryMismatch() {
        ExamInstance instance = instance(CameraMode.ON);
        UserAccount me = user(userId);
        String token = "raw-token";
        String hash = TokenHashing.sha256Hex(token);
        ProctorSnapshot good = checkSnapshot(instance, me);
        when(snapshots.findByCheckTokenHash(hash)).thenReturn(Optional.of(good));

        assertThatThrownBy(() -> service.requireCheck(caller, instance, null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ATT_CAMERA_CHECK_REQUIRED);
        assertThatThrownBy(() -> service.requireCheck(caller, instance, "  ")).isInstanceOf(BusinessException.class);
        String unknownHash = TokenHashing.sha256Hex("unknown");
        when(snapshots.findByCheckTokenHash(unknownHash)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireCheck(caller, instance, "unknown")).isInstanceOf(BusinessException.class);

        ProctorSnapshot foreign = checkSnapshot(instance, me);
        foreign.setCentreId(UUID.randomUUID());
        when(snapshots.findByCheckTokenHash(hash)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.requireCheck(caller, instance, token)).isInstanceOf(BusinessException.class);

        ExamInstance other = instance(CameraMode.ON);
        ProctorSnapshot wrongInstance = checkSnapshot(other, me);
        when(snapshots.findByCheckTokenHash(hash)).thenReturn(Optional.of(wrongInstance));
        assertThatThrownBy(() -> service.requireCheck(caller, instance, token)).isInstanceOf(BusinessException.class);

        UserAccount stranger = user(UUID.randomUUID());
        ProctorSnapshot wrongUser = checkSnapshot(instance, stranger);
        when(snapshots.findByCheckTokenHash(hash)).thenReturn(Optional.of(wrongUser));
        assertThatThrownBy(() -> service.requireCheck(caller, instance, token)).isInstanceOf(BusinessException.class);

        ProctorSnapshot wrongReason = checkSnapshot(instance, me);
        wrongReason.setReason(SnapshotReason.PERIODIC);
        when(snapshots.findByCheckTokenHash(hash)).thenReturn(Optional.of(wrongReason));
        assertThatThrownBy(() -> service.requireCheck(caller, instance, token)).isInstanceOf(BusinessException.class);

        ProctorSnapshot used = checkSnapshot(instance, me);
        used.setCheckUsedAt(NOW);
        when(snapshots.findByCheckTokenHash(hash)).thenReturn(Optional.of(used));
        assertThatThrownBy(() -> service.requireCheck(caller, instance, token)).isInstanceOf(BusinessException.class);

        ProctorSnapshot expired = checkSnapshot(instance, me);
        expired.setCheckExpiresAt(NOW.minusSeconds(1));
        when(snapshots.findByCheckTokenHash(hash)).thenReturn(Optional.of(expired));
        assertThatThrownBy(() -> service.requireCheck(caller, instance, token)).isInstanceOf(BusinessException.class);

        when(snapshots.findByCheckTokenHash(hash)).thenReturn(Optional.of(good));
        assertThat(service.requireCheck(caller, instance, token)).isSameAs(good);
    }

    @Test
    void imageExtensionRejectsNearMissHeaders() {
        byte[][] cases = {
            {(byte) 0xFF, 0x00, (byte) 0xFF},
            {(byte) 0xFF, (byte) 0xD8, 0x00},
            {(byte) 0x89, 0x00, 0x4E, 0x47},
            {(byte) 0x89, 0x50, 0x00, 0x47},
            {(byte) 0x89, 0x50, 0x4E, 0x00},
            {0x00, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50},
            {0x52, 0x00, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50},
            {0x52, 0x49, 0x00, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50},
            {0x52, 0x49, 0x46, 0x00, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50},
            {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42},
            {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x00},
            {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x00, 0x50},
            {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x00, 0x42, 0x50},
            {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x00, 0x45, 0x42, 0x50},
        };
        for (byte[] nearMiss : cases) {
            assertThatThrownBy(() -> ProctorService.imageExtension(nearMiss)).isInstanceOf(BusinessException.class);
        }
    }

    @Test
    void uploadSnapshotRejectsForeignCentreAttempt() {
        UUID attemptId = UUID.randomUUID();
        ExamAttempt foreign = mock(ExamAttempt.class);
        when(foreign.getCentreId()).thenReturn(UUID.randomUUID());
        when(examAttempts.findById(attemptId)).thenReturn(Optional.of(foreign));
        MockMultipartFile frame = new MockMultipartFile("file", "frame.jpg", "image/jpeg", JPEG);
        assertThatThrownBy(() -> service.uploadSnapshot(caller, attemptId, frame, "PERIODIC"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ATT_NOT_ASSIGNED);
    }

    @Test
    void consumeCheckLinksTheAttempt() {
        ExamInstance instance = instance(CameraMode.ON);
        UserAccount me = user(userId);
        ProctorSnapshot snapshot = checkSnapshot(instance, me);
        ExamAttempt attempt = mock(ExamAttempt.class);
        service.consumeCheck(snapshot, attempt);
        assertThat(snapshot.getAttempt()).isSameAs(attempt);
        assertThat(snapshot.getCheckUsedAt()).isEqualTo(NOW);
        verify(snapshots).save(snapshot);
    }

    @Test
    void uploadSnapshotValidationAndHappyPath() {
        UUID attemptId = UUID.randomUUID();
        MockMultipartFile frame = new MockMultipartFile("file", "frame.jpg", "image/jpeg", JPEG);
        assertThatThrownBy(() -> service.uploadSnapshot(caller, attemptId, frame, "BOGUS"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.uploadSnapshot(caller, attemptId, frame, null))
                .isInstanceOf(BusinessException.class);

        ExamInstance on = instance(CameraMode.ON);
        UserAccount me = user(userId);
        ExamAttempt running = attempt(on, ExamAttemptStatus.IN_PROGRESS, me);
        when(examAttempts.findById(attemptId)).thenReturn(Optional.of(running));
        assertThatThrownBy(() -> service.uploadSnapshot(caller, attemptId, frame, "PRE_TEST"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getMessage())
                .isEqualTo("PRE_TEST frames come from the camera check");

        when(examAttempts.findById(attemptId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.uploadSnapshot(caller, attemptId, frame, "PERIODIC"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ATT_NOT_ASSIGNED);

        UserAccount stranger = user(UUID.randomUUID());
        ExamAttempt foreign = attempt(on, ExamAttemptStatus.IN_PROGRESS, stranger);
        when(examAttempts.findById(attemptId)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.uploadSnapshot(caller, attemptId, frame, "PERIODIC"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ATT_NOT_ASSIGNED);

        ExamInstance off = instance(CameraMode.OFF);
        ExamAttempt offAttempt = attempt(off, ExamAttemptStatus.IN_PROGRESS, me);
        when(examAttempts.findById(attemptId)).thenReturn(Optional.of(offAttempt));
        assertThatThrownBy(() -> service.uploadSnapshot(caller, attemptId, frame, "PERIODIC"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getMessage())
                .isEqualTo("Camera is not enabled for this test");

        ExamAttempt submitted = attempt(on, ExamAttemptStatus.SUBMITTED, me);
        when(examAttempts.findById(attemptId)).thenReturn(Optional.of(submitted));
        assertThatThrownBy(() -> service.uploadSnapshot(caller, attemptId, frame, "PERIODIC"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.ATT_NOT_AVAILABLE);

        when(running.getAttemptId()).thenReturn(attemptId);
        when(examAttempts.findById(attemptId)).thenReturn(Optional.of(running));
        when(storage.store(any(), any(), any(), any())).thenReturn("frame.jpg");
        var dto = service.uploadSnapshot(caller, attemptId, frame, "EVENT");
        assertThat(dto.reason()).isEqualTo("EVENT");
        verify(snapshots).save(any(ProctorSnapshot.class));
    }
}
