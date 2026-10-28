package com.learnium.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.learnium.api.dto.AuthDtos.CreateTeacherRequest;
import com.learnium.auth.AccountFlowService;
import com.learnium.auth.RefreshTokenService;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserRole;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.LearniumPrincipal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

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

    @Test
    void databaseUniquenessViolationSurfacesAsEmailTaken() {
        // The friendly pre-check passed (a concurrent create won the race);
        // the V8 unique index rejects the insert at flush time and the
        // service translates it into the same email-in-use error.
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        CentreRepository centreRepository = mock(CentreRepository.class);
        UUID centreId = UUID.randomUUID();
        when(userAccountRepository.existsByEmail("raced@alpha.test")).thenReturn(false);
        when(centreRepository.findById(centreId)).thenReturn(Optional.of(new Centre()));
        when(userAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new DataIntegrityViolationException("uq_user_account_email_global"))
                .when(userAccountRepository)
                .flush();
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
                                        principal,
                                        new CreateTeacherRequest("Raced@Alpha.test", "Raced Teacher")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USR_EMAIL_TAKEN));
    }
}
