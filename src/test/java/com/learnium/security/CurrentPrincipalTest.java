package com.learnium.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.learnium.common.error.BusinessException;
import com.learnium.model.user.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentPrincipalTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsTheLearniumPrincipalFromTheContext() {
        LearniumPrincipal principal =
                LearniumPrincipal.staff(UUID.randomUUID(), UUID.randomUUID(), UserRole.TEACHER);
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));

        assertThat(CurrentPrincipal.require()).isSameAs(principal);
    }

    @Test
    void missingAuthenticationThrowsUnauthenticated() {
        assertThatThrownBy(CurrentPrincipal::require)
                .isInstanceOf(BusinessException.class)
                .hasMessage("Authentication required");
    }

    @Test
    void foreignPrincipalTypeThrowsUnauthenticated() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken("someone", null, List.of()));

        assertThatThrownBy(CurrentPrincipal::require).isInstanceOf(BusinessException.class);
    }
}
