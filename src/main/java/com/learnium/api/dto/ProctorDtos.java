package com.learnium.api.dto;

import java.time.Instant;
import java.util.UUID;

public final class ProctorDtos {
    private ProctorDtos() {}

    /** The raw check token is returned once; only its hash is stored. */
    public record CameraCheckDto(String checkToken, Instant expiresAt) {}

    public record SnapshotDto(UUID snapshotId, String reason, Instant capturedAt) {}
}
