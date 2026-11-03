package com.learnium.security;

import com.learnium.model.user.UserRole;
import java.util.UUID;

/**
 * The authenticated caller, built from a validated JWT. {@code centreId} is the
 * ONLY source of tenancy for service code — it always comes from the token,
 * never from a request parameter. Students are real centre-scoped accounts;
 * assignment and roster scope is resolved by each service from persisted data.
 */
public record LearniumPrincipal(UUID userId, UUID centreId, UserRole role) {

    public static LearniumPrincipal staff(UUID userId, UUID centreId, UserRole role) {
        return new LearniumPrincipal(userId, centreId, role);
    }

    public static LearniumPrincipal student(UUID userId, UUID centreId) {
        return new LearniumPrincipal(userId, centreId, UserRole.STUDENT);
    }

    public boolean isStudent() {
        return role == UserRole.STUDENT;
    }
}
