package com.learnium.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.learnium.api.dto.AdminDtos.CreateCentreRequest;
import com.learnium.auth.AccountFlowService;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.user.Centre;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/** Onboarding with a null (not merely blank) configured provisioning key. */
class PlatformServiceTest {

    @Test
    void nullProvisioningKeyDisablesOnboarding() {
        PlatformService service =
                new PlatformService(
                        mock(CentreRepository.class),
                        mock(UserAccountRepository.class),
                        mock(AccountFlowService.class),
                        null);

        assertThatThrownBy(
                        () ->
                                service.createCentre(
                                        "any-key",
                                        new CreateCentreRequest("New Centre", "owner@new.test", "Olivia")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.SYS_NOT_FOUND));
    }

    @Test
    void databaseUniquenessViolationSurfacesAsEmailTaken() {
        // The pre-check passed but the V8 unique index rejects the admin
        // insert at flush time (a concurrent onboarding won the race).
        CentreRepository centreRepository = mock(CentreRepository.class);
        UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
        when(userAccountRepository.existsByEmail("owner@new.test")).thenReturn(false);
        when(centreRepository.save(any()))
                .thenAnswer(
                        invocation -> {
                            Centre centre = invocation.getArgument(0);
                            centre.setCentreId(java.util.UUID.randomUUID());
                            return centre;
                        });
        when(userAccountRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new DataIntegrityViolationException("uq_user_account_email_global"))
                .when(userAccountRepository)
                .flush();
        PlatformService service =
                new PlatformService(
                        centreRepository,
                        userAccountRepository,
                        mock(AccountFlowService.class),
                        "provisioning-key");

        assertThatThrownBy(
                        () ->
                                service.createCentre(
                                        "provisioning-key",
                                        new CreateCentreRequest("New Centre", "Owner@New.test", "Olivia")))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.USR_EMAIL_TAKEN));
    }
}
