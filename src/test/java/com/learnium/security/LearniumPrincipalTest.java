package com.learnium.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.learnium.model.user.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The principal record's factories and the student check. */
class LearniumPrincipalTest {

    @Test
    void staffFactoryCarriesNoBatchScope() {
        UUID userId = UUID.randomUUID();
        UUID centreId = UUID.randomUUID();

        LearniumPrincipal principal = LearniumPrincipal.staff(userId, centreId, UserRole.TEACHER);

        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.centreId()).isEqualTo(centreId);
        assertThat(principal.role()).isEqualTo(UserRole.TEACHER);
        assertThat(principal.batchId()).isNull();
        assertThat(principal.membershipId()).isNull();
        assertThat(principal.isStudent()).isFalse();
    }

    @Test
    void studentFactoryCarriesBatchScope() {
        UUID batchId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        LearniumPrincipal principal =
                LearniumPrincipal.student(UUID.randomUUID(), UUID.randomUUID(), batchId, membershipId);

        assertThat(principal.role()).isEqualTo(UserRole.STUDENT);
        assertThat(principal.batchId()).isEqualTo(batchId);
        assertThat(principal.membershipId()).isEqualTo(membershipId);
        assertThat(principal.isStudent()).isTrue();
    }
}
