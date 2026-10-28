package com.learnium.admin;

import com.learnium.api.dto.AdminDtos.CreateCentreRequest;
import com.learnium.api.dto.AdminDtos.CreateCentreResponse;
import com.learnium.auth.AccountFlowService;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.common.UserStatus;
import com.learnium.model.user.Centre;
import com.learnium.model.user.CentreStatus;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Platform onboarding: creates a centre and its first admin (who sets their
 * password through the emailed invite). The endpoint is disabled unless a
 * provisioning key is configured — with no key configured it answers 404, as
 * if the route did not exist.
 */
@Service
public class PlatformService {

    private final CentreRepository centreRepository;
    private final UserAccountRepository userAccountRepository;
    private final AccountFlowService accountFlowService;
    private final String provisioningKey;

    public PlatformService(
            CentreRepository centreRepository,
            UserAccountRepository userAccountRepository,
            AccountFlowService accountFlowService,
            @Value("${learnium.platform.provisioning-key:}") String provisioningKey) {
        this.centreRepository = centreRepository;
        this.userAccountRepository = userAccountRepository;
        this.accountFlowService = accountFlowService;
        this.provisioningKey = provisioningKey;
    }

    @Transactional
    public CreateCentreResponse createCentre(String providedKey, CreateCentreRequest request) {
        requireProvisioningKey(providedKey);
        if (userAccountRepository.existsByEmail(request.adminEmail())) {
            throw new BusinessException(ErrorCode.USR_EMAIL_TAKEN);
        }
        Centre centre = new Centre();
        centre.setName(request.name());
        centre.setStatus(CentreStatus.ACTIVE);
        centre = centreRepository.save(centre);

        UserAccount admin = new UserAccount();
        admin.setCentre(centre);
        admin.setRole(UserRole.CENTRE_ADMIN);
        admin.setEmail(request.adminEmail());
        admin.setDisplayName(request.adminDisplayName());
        admin.setPasswordHash(null);
        admin.setStatus(UserStatus.ACTIVE);
        admin = userAccountRepository.save(admin);

        accountFlowService.sendInvite(admin);
        return new CreateCentreResponse(centre.getCentreId(), admin.getUserId());
    }

    private void requireProvisioningKey(String providedKey) {
        if (provisioningKey == null || provisioningKey.isBlank()) {
            // Disabled: behave as if the route does not exist.
            throw new BusinessException(ErrorCode.SYS_NOT_FOUND);
        }
        boolean matches =
                providedKey != null
                        && MessageDigest.isEqual(
                                provisioningKey.getBytes(StandardCharsets.UTF_8),
                                providedKey.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new BusinessException(ErrorCode.AUTH_UNAUTHENTICATED);
        }
    }
}
