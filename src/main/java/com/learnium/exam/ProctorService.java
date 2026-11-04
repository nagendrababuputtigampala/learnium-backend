package com.learnium.exam;

import com.learnium.api.dto.ProctorDtos.CameraCheckDto;
import com.learnium.api.dto.ProctorDtos.SnapshotDto;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.exam.CameraMode;
import com.learnium.model.exam.ExamAttempt;
import com.learnium.model.exam.ExamAttemptStatus;
import com.learnium.model.exam.ExamInstance;
import com.learnium.model.exam.ProctorSnapshot;
import com.learnium.model.exam.SnapshotReason;
import com.learnium.repository.exam.ExamAttemptRepository;
import com.learnium.repository.exam.ExamInstanceRepository;
import com.learnium.repository.exam.ExamInstanceStudentRepository;
import com.learnium.repository.exam.ProctorSnapshotRepository;
import com.learnium.security.LearniumPrincipal;
import com.learnium.security.TokenHashing;
import com.learnium.storage.StorageService;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Camera proctoring: a pre-test camera check issues a short-lived single-use
 * token (only its hash is stored, on the PRE_TEST snapshot row) that the
 * start call requires for camera-ON instances. Frames are still images only —
 * no video, no image processing — stored via the StorageService.
 */
@Service
public class ProctorService {
    static final long MAX_IMAGE_BYTES = 2_000_000;
    static final Duration CHECK_TTL = Duration.ofMinutes(10);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ExamInstanceStudentRepository assignments;
    private final ExamInstanceRepository instances;
    private final ExamAttemptRepository examAttempts;
    private final ProctorSnapshotRepository snapshots;
    private final StorageService storage;
    private final Clock clock;

    public ProctorService(
            ExamInstanceStudentRepository assignments,
            ExamInstanceRepository instances,
            ExamAttemptRepository examAttempts,
            ProctorSnapshotRepository snapshots,
            StorageService storage,
            Clock clock) {
        this.assignments = assignments;
        this.instances = instances;
        this.examAttempts = examAttempts;
        this.snapshots = snapshots;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional
    public CameraCheckDto cameraCheck(LearniumPrincipal caller, UUID instanceId, MultipartFile file) {
        var assignment =
                assignments
                        .findAssigned(caller.centreId(), instanceId, caller.userId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.ATT_NOT_ASSIGNED));
        ExamInstance instance =
                instances
                        .findById(instanceId)
                        .filter(row -> row.getCentreId().equals(caller.centreId()))
                        .orElseThrow(() -> new BusinessException(ErrorCode.ATT_NOT_ASSIGNED));
        if (!cameraOn(instance)) throw new BusinessException(ErrorCode.VAL_FAILED, "Camera is not enabled for this test");
        byte[] content = imageBytes(file);
        String extension = imageExtension(content);
        Instant now = clock.instant();
        ProctorSnapshot snapshot = new ProctorSnapshot();
        snapshot.setCentreId(caller.centreId());
        snapshot.setExamInstance(instance);
        snapshot.setUser(assignment.getUser());
        snapshot.setReason(SnapshotReason.PRE_TEST);
        snapshot.setCapturedAt(now);
        String rawToken = newToken();
        snapshot.setCheckTokenHash(TokenHashing.sha256Hex(rawToken));
        snapshot.setCheckExpiresAt(now.plus(CHECK_TTL));
        snapshot.setStorageKey(store(caller.centreId(), instanceId, extension, content));
        snapshots.save(snapshot);
        return new CameraCheckDto(rawToken, snapshot.getCheckExpiresAt());
    }

    /** Validates (without consuming) the check token presented at start; throws LRN-ATT-004 when unusable. */
    @Transactional(readOnly = true)
    public ProctorSnapshot requireCheck(LearniumPrincipal caller, ExamInstance instance, String rawToken) {
        if (rawToken == null || rawToken.isBlank()) throw new BusinessException(ErrorCode.ATT_CAMERA_CHECK_REQUIRED);
        ProctorSnapshot snapshot =
                snapshots
                        .findByCheckTokenHash(TokenHashing.sha256Hex(rawToken))
                        .filter(row -> row.getCentreId().equals(caller.centreId()))
                        .filter(row -> row.getExamInstance().getExamInstanceId().equals(instance.getExamInstanceId()))
                        .filter(row -> row.getUser().getUserId().equals(caller.userId()))
                        .filter(row -> row.getReason() == SnapshotReason.PRE_TEST)
                        .filter(row -> row.getCheckUsedAt() == null)
                        .filter(row -> row.getCheckExpiresAt().isAfter(clock.instant()))
                        .orElseThrow(() -> new BusinessException(ErrorCode.ATT_CAMERA_CHECK_REQUIRED));
        return snapshot;
    }

    /** Links the PRE_TEST snapshot to the attempt it unlocked and burns the token. */
    @Transactional
    public void consumeCheck(ProctorSnapshot snapshot, ExamAttempt attempt) {
        snapshot.setAttempt(attempt);
        snapshot.setCheckUsedAt(clock.instant());
        snapshots.save(snapshot);
    }

    static boolean cameraOn(ExamInstance instance) {
        return instance.getCameraMode() == CameraMode.ON || instance.getCameraMode() == CameraMode.VERIFY_SNAPSHOTS;
    }

    /** One in-attempt frame (PERIODIC cadence or EVENT-triggered) for the student's own in-progress attempt. */
    @Transactional
    public SnapshotDto uploadSnapshot(LearniumPrincipal caller, UUID attemptId, MultipartFile file, String reason) {
        SnapshotReason parsed = parseReason(reason);
        ExamAttempt attempt =
                examAttempts
                        .findById(attemptId)
                        .filter(
                                row ->
                                        row.getCentreId().equals(caller.centreId())
                                                && row.getUser().getUserId().equals(caller.userId()))
                        .orElseThrow(() -> new BusinessException(ErrorCode.ATT_NOT_ASSIGNED));
        if (!cameraOn(attempt.getExamInstance()))
            throw new BusinessException(ErrorCode.VAL_FAILED, "Camera is not enabled for this test");
        if (attempt.getStatus() != ExamAttemptStatus.IN_PROGRESS)
            throw new BusinessException(ErrorCode.ATT_NOT_AVAILABLE, "Snapshots can only be added while the test is running");
        byte[] content = imageBytes(file);
        String extension = imageExtension(content);
        ProctorSnapshot snapshot = new ProctorSnapshot();
        snapshot.setCentreId(caller.centreId());
        snapshot.setAttempt(attempt);
        snapshot.setExamInstance(attempt.getExamInstance());
        snapshot.setUser(attempt.getUser());
        snapshot.setReason(parsed);
        snapshot.setCapturedAt(clock.instant());
        snapshot.setStorageKey(
                store(caller.centreId(), attempt.getExamInstance().getExamInstanceId(), extension, content));
        snapshots.save(snapshot);
        return new SnapshotDto(snapshot.getSnapshotId(), snapshot.getReason().name(), snapshot.getCapturedAt());
    }

    private SnapshotReason parseReason(String reason) {
        SnapshotReason parsed;
        try {
            parsed = SnapshotReason.valueOf(reason == null ? "" : reason);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.VAL_FAILED, "Unknown snapshot reason");
        }
        if (parsed == SnapshotReason.PRE_TEST)
            throw new BusinessException(ErrorCode.VAL_FAILED, "PRE_TEST frames come from the camera check");
        return parsed;
    }

    /** Deletes snapshots (object + row) captured more than 30 days ago. Returns how many were purged. */
    @Transactional
    public int purgeExpired() {
        var expired = snapshots.findByCapturedAtBefore(clock.instant().minus(Duration.ofDays(30)));
        for (ProctorSnapshot snapshot : expired) {
            storage.delete(snapshot.getStorageKey());
            snapshots.delete(snapshot);
        }
        return expired.size();
    }

    private String store(UUID centreId, UUID instanceId, String extension, byte[] content) {
        String filename = "proctor-" + UUID.randomUUID() + extension;
        return storage.store(centreId, instanceId, filename, content);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Reads and size-caps an uploaded frame; the magic-byte check rejects non-images whatever the filename claims. */
    static byte[] imageBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(ErrorCode.VAL_FAILED, "A camera frame is required");
        if (file.getSize() > MAX_IMAGE_BYTES) throw new BusinessException(ErrorCode.VAL_FAILED, "Camera frame is too large");
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.VAL_FAILED, "Camera frame could not be read");
        }
    }

    static String imageExtension(byte[] content) {
        if (content.length >= 3 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF) {
            return ".jpg";
        }
        if (content.length >= 4 && (content[0] & 0xFF) == 0x89 && content[1] == 0x50 && content[2] == 0x4E && content[3] == 0x47) {
            return ".png";
        }
        if (content.length >= 12 && content[0] == 0x52 && content[1] == 0x49 && content[2] == 0x46 && content[3] == 0x46
                && content[8] == 0x57 && content[9] == 0x45 && content[10] == 0x42 && content[11] == 0x50) {
            return ".webp";
        }
        throw new BusinessException(ErrorCode.VAL_FAILED, "Camera frame must be a JPEG, PNG or WebP image");
    }
}
