package com.learnium.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.learnium.api.dto.AuthDtos.CreateTeacherRequest;
import com.learnium.auth.AccountFlowService;
import com.learnium.auth.RefreshTokenService;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.user.UserRole;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.LearniumPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Teacher creation when the caller's centre row itself is missing. */
class TeacherAdminServiceTest {

    @Test
    void createTeacherWithMissingCentreThrowsSysNotFound() {
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        CentreRepository centreRepository = mock(CentreRepository.class);
        UUID centreId = UUID.randomUUID();
        when(userAccountRepository.existsByEmail("new@alpha.test")).thenReturn(false);
        when(centreRepository.findById(centreId)).thenReturn(Optional.empty());
        TeacherAdminService service =
                new TeacherAdminService(
                        userAccountRepository,
                        centreRepository,
                        mock(AccountFlowService.class),
                        mock(RefreshTokenService.class));
        LearniumPrincipal principal =
                LearniumPrincipal.staff(UUID.randomUUID(), centreId, UserRole.CENTRE_ADMIN);

        assertThatThrownBy(
                        () ->
                                service.createTeacher(
                                        principal, new CreateTeacherRequest("new@alpha.test", "New Teacher")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SYS_NOT_FOUND));
    }
}
