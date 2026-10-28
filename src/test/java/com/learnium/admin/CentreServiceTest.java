package com.learnium.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.user.UserRole;
import com.learnium.repository.user.CentreRepository;
import com.learnium.security.LearniumPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The centre profile service's defensive not-found path. */
class CentreServiceTest {

    @Test
    void getCentreForAMissingCentreThrowsSysNotFound() {
        CentreRepository centreRepository = mock(CentreRepository.class);
        UUID centreId = UUID.randomUUID();
        when(centreRepository.findById(centreId)).thenReturn(Optional.empty());
        CentreService service = new CentreService(centreRepository);
        LearniumPrincipal principal =
                LearniumPrincipal.staff(UUID.randomUUID(), centreId, UserRole.CENTRE_ADMIN);

        assertThatThrownBy(() -> service.getCentre(principal))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SYS_NOT_FOUND));
    }
}
