package com.learnium.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class IntegrityDtos {
    private IntegrityDtos() {}

    public record IntegrityEventRequest(String type, Instant occurredAt) {}

    public record IntegrityEventsRequest(List<IntegrityEventRequest> events) {}

    public record IntegrityEventsResponse(int recorded) {}

    public record IntegrityEventDto(UUID eventId, String type, Instant occurredAt) {}
}
