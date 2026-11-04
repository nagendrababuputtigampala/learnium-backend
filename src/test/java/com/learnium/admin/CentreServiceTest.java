package com.learnium.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.user.UserRole;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.LearniumPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The centre profile service's defensive not-found path. */
class CentreServiceTest {

    private CentreService serviceWithCentre(com.learnium.model.user.Centre centre) {
        CentreRepository centreRepository = mock(CentreRepository.class);
        when(centreRepository.findById(centre.getCentreId())).thenReturn(Optional.of(centre));
        when(centreRepository.save(centre)).thenReturn(centre);
        return new CentreService(centreRepository, mock(UserAccountRepository.class), mock(StudentUsernameService.class));
    }

    private LearniumPrincipal adminOf(UUID centreId) {
        return LearniumPrincipal.staff(UUID.randomUUID(), centreId, UserRole.CENTRE_ADMIN);
    }

    @Test
    void updateCentreAppliesAValidTimezone() {
        com.learnium.model.user.Centre centre = new com.learnium.model.user.Centre();
        centre.setCentreId(UUID.randomUUID());
        centre.setCode("tzc");
        CentreService service = serviceWithCentre(centre);

        var dto = service.updateCentre(
                adminOf(centre.getCentreId()),
                new com.learnium.api.dto.AdminDtos.UpdateCentreRequest("Name", null, null, "Asia/Kolkata"));

        assertThat(dto.timezone()).isEqualTo("Asia/Kolkata");
        assertThat(centre.getTimezone()).isEqualTo("Asia/Kolkata");
    }

    @Test
    void updateCentreRejectsAnUnknownTimezone() {
        com.learnium.model.user.Centre centre = new com.learnium.model.user.Centre();
        centre.setCentreId(UUID.randomUUID());
        centre.setCode("tzc");
        CentreService service = serviceWithCentre(centre);

        assertThatThrownBy(
                        () -> service.updateCentre(
                                adminOf(centre.getCentreId()),
                                new com.learnium.api.dto.AdminDtos.UpdateCentreRequest(
                                        "Name", null, null, "Mars/Olympus")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VAL_FAILED));
    }

    @Test
    void updateCentreWithoutTimezoneKeepsTheCurrentOne() {
        com.learnium.model.user.Centre centre = new com.learnium.model.user.Centre();
        centre.setCentreId(UUID.randomUUID());
        centre.setCode("tzc");
        centre.setTimezone("America/Toronto");
        CentreService service = serviceWithCentre(centre);

        var dto = service.updateCentre(
                adminOf(centre.getCentreId()),
                new com.learnium.api.dto.AdminDtos.UpdateCentreRequest("Name", null, null, null));

        assertThat(dto.timezone()).isEqualTo("America/Toronto");
    }

    @Test
    void getCentreForAMissingCentreThrowsSysNotFound() {
        CentreRepository centreRepository = mock(CentreRepository.class);
        UUID centreId = UUID.randomUUID();
        when(centreRepository.findById(centreId)).thenReturn(Optional.empty());
        CentreService service = new CentreService(centreRepository, mock(UserAccountRepository.class), mock(StudentUsernameService.class));
        LearniumPrincipal principal =
                LearniumPrincipal.staff(UUID.randomUUID(), centreId, UserRole.CENTRE_ADMIN);

        assertThatThrownBy(() -> service.getCentre(principal))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SYS_NOT_FOUND));
    }
}
