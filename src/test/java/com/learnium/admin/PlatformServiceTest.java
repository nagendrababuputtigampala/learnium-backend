package com.learnium.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.learnium.api.dto.AdminDtos.CreateCentreRequest;
import com.learnium.auth.AccountFlowService;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import org.junit.jupiter.api.Test;

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
}
