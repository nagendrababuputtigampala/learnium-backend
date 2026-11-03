package com.learnium.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.learnium.model.user.UserRole;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The account principal factories and student role check. */
class LearniumPrincipalTest {

    @Test
    void staffFactoryCarriesAccountScope() {
        UUID userId = UUID.randomUUID();
        UUID centreId = UUID.randomUUID();

        LearniumPrincipal principal = LearniumPrincipal.staff(userId, centreId, UserRole.TEACHER);

        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.centreId()).isEqualTo(centreId);
        assertThat(principal.role()).isEqualTo(UserRole.TEACHER);
        assertThat(principal.isStudent()).isFalse();
    }

    @Test
    void studentFactoryCarriesAccountScope() {
        UUID userId = UUID.randomUUID();
        UUID centreId = UUID.randomUUID();
        LearniumPrincipal principal = LearniumPrincipal.student(userId, centreId);

        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.centreId()).isEqualTo(centreId);
        assertThat(principal.role()).isEqualTo(UserRole.STUDENT);
        assertThat(principal.isStudent()).isTrue();
    }
}
