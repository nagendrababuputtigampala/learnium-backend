package com.learnium.admin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.learnium.common.error.BusinessException;
import com.learnium.model.user.*;
import com.learnium.repository.user.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class StudentUsernameServiceTest {
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final CentreRepository centres = mock(CentreRepository.class);
    private final StudentUsernameService service = new StudentUsernameService(users, centres);
    private final UUID centreId = UUID.randomUUID();

    @Test
    void normalizesValidatesAndSuggestsNames() {
        assertThat(StudentUsernameService.normalize(" Simran.K ")).isEqualTo("simran.k");
        assertThat(StudentUsernameService.normalize(null)).isEmpty();
        assertThat(StudentUsernameService.valid("abc")).isTrue();
        assertThat(StudentUsernameService.valid("a.b")).isTrue();
        assertThat(StudentUsernameService.valid(".abc")).isFalse();
        assertThat(StudentUsernameService.valid("abc.")).isFalse();
        assertThat(StudentUsernameService.valid("a..b")).isFalse();
        assertThat(StudentUsernameService.valid("ab")).isFalse();
        assertThat(StudentUsernameService.valid("a".repeat(31))).isFalse();
        assertThat(StudentUsernameService.suggestion("Simran Kaur")).isEqualTo("simran.k");
        assertThat(StudentUsernameService.suggestion("Élodie")).isEqualTo("elodie");
        assertThat(StudentUsernameService.suggestion(null)).isEqualTo("student");
        assertThat(StudentUsernameService.suggestion("-Élodie")).isEqualTo("elodie");
        assertThat(StudentUsernameService.suggestion("Li")).isEqualTo("lis");
        assertThat(StudentUsernameService.suggestion("!!!")).isEqualTo("student");
        assertThat(StudentUsernameService.suggestion("a".repeat(31))).hasSize(30);
    }

    @Test
    void availabilityRequiresAValidUnusedName() {
        when(users.existsByCentreIdAndUsername(centreId, "simran.k")).thenReturn(false, true);
        assertThat(service.available(centreId, "Simran.K")).isTrue();
        assertThat(service.available(centreId, "simran.k")).isFalse();
        assertThat(service.available(centreId, "bad username")).isFalse();
    }

    @Test
    void customChoiceLocksTheCentreAndRejectsBadOrTakenNames() {
        when(centres.lockById(centreId)).thenReturn(Optional.of(new Centre()));
        assertThat(service.choose(centreId, "Ignored", " Custom.Name ")).isEqualTo("custom.name");
        assertThatThrownBy(() -> service.choose(centreId, "Ignored", "bad name"))
                .isInstanceOf(BusinessException.class);
        when(users.existsByCentreIdAndUsername(centreId, "taken.name")).thenReturn(true);
        assertThatThrownBy(() -> service.choose(centreId, "Ignored", "taken.name"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void generatedChoiceUsesANumericSuffixAndColumnLimit() {
        when(centres.lockById(centreId)).thenReturn(Optional.of(new Centre()));
        when(users.existsByCentreIdAndUsername(centreId, "simran.k")).thenReturn(true);
        when(users.existsByCentreIdAndUsername(centreId, "simran.k2")).thenReturn(true);
        when(users.existsByCentreIdAndUsername(centreId, "simran.k3")).thenReturn(false);
        assertThat(service.choose(centreId, "Simran Kaur", null)).isEqualTo("simran.k3");

        String base = "a".repeat(30);
        when(users.existsByCentreIdAndUsername(centreId, base)).thenReturn(true);
        when(users.existsByCentreIdAndUsername(centreId, "a".repeat(29) + "2")).thenReturn(false);
        assertThat(service.choose(centreId, base, "")).isEqualTo("a".repeat(29) + "2");
    }

    @Test
    void choiceFailsForAnUnknownCentre() {
        when(centres.lockById(centreId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.choose(centreId, "Simran", null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void renameAllowsTheCurrentNameButRejectsTakenAndMissingUsers() {
        UUID userId = UUID.randomUUID();
        Centre centre = new Centre();
        UserAccount student = new UserAccount();
        student.setCentre(centre);
        student.setRole(UserRole.STUDENT);
        student.setUsername("simran");
        when(centres.lockById(centreId)).thenReturn(Optional.of(centre));
        when(users.findByCentreIdAndUserId(centreId, userId)).thenReturn(Optional.of(student));
        assertThat(service.requireAvailable(centreId, "Simran", userId)).isEqualTo("simran");

        when(users.existsByCentreIdAndUsername(centreId, "other.name")).thenReturn(true);
        assertThatThrownBy(() -> service.requireAvailable(centreId, "other.name", userId))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.requireAvailable(centreId, "bad name", userId))
                .isInstanceOf(BusinessException.class);
        when(users.findByCentreIdAndUserId(centreId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireAvailable(centreId, "valid.name", userId))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void renameFailsForAnUnknownCentre() {
        when(centres.lockById(centreId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireAvailable(centreId, "valid.name", UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
    }
}
