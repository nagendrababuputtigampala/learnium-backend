package com.learnium.security;

import com.learnium.model.user.UserRole;
import java.util.UUID;

/**
 * The authenticated caller, built from a validated JWT. {@code centreId} is the
 * ONLY source of tenancy for service code — it always comes from the token,
 * never from a request parameter. {@code batchId} / {@code membershipId} are
 * set for student tokens only (a student session is scoped to one roster row).
 */
public record LearniumPrincipal(
        UUID userId, UUID centreId, UserRole role, UUID batchId, UUID membershipId) {

    public static LearniumPrincipal staff(UUID userId, UUID centreId, UserRole role) {
        return new LearniumPrincipal(userId, centreId, role, null, null);
    }

    public static LearniumPrincipal student(
            UUID userId, UUID centreId, UUID batchId, UUID membershipId) {
        return new LearniumPrincipal(userId, centreId, UserRole.STUDENT, batchId, membershipId);
    }

    public boolean isStudent() {
        return role == UserRole.STUDENT;
    }
}
