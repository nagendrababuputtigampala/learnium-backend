package com.learnium.admin;

import com.learnium.api.dto.AuthDtos.CreateTeacherRequest;
import com.learnium.api.dto.AuthDtos.TeacherDto;
import com.learnium.auth.AccountFlowService;
import com.learnium.auth.RefreshTokenService;
import com.learnium.common.api.PageResponse;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.common.UserStatus;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.LearniumPrincipal;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Centre-admin teacher management. Creating a teacher creates the account
 * (no password yet) and emails a 48-hour invite in the same transaction.
 * Staff emails are globally unique — the invariant the login door relies on.
 */
@Service
public class TeacherAdminService {

    private final UserAccountRepository userAccountRepository;
    private final CentreRepository centreRepository;
    private final AccountFlowService accountFlowService;
    private final RefreshTokenService refreshTokenService;

    public TeacherAdminService(
            UserAccountRepository userAccountRepository,
            CentreRepository centreRepository,
            AccountFlowService accountFlowService,
            RefreshTokenService refreshTokenService) {
        this.userAccountRepository = userAccountRepository;
        this.centreRepository = centreRepository;
        this.accountFlowService = accountFlowService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public TeacherDto createTeacher(LearniumPrincipal principal, CreateTeacherRequest request) {
        if (userAccountRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.USR_EMAIL_TAKEN);
        }
        Centre centre =
                centreRepository
                        .findById(principal.centreId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.SYS_NOT_FOUND));
        UserAccount teacher = new UserAccount();
        teacher.setCentre(centre);
        teacher.setRole(UserRole.TEACHER);
        teacher.setEmail(request.email());
        teacher.setDisplayName(request.displayName());
        teacher.setPasswordHash(null);
        teacher.setStatus(UserStatus.ACTIVE);
        teacher = userAccountRepository.save(teacher);
        accountFlowService.sendInvite(teacher);
        return toDto(teacher);
    }

    @Transactional(readOnly = true)
    public PageResponse<TeacherDto> listTeachers(LearniumPrincipal principal, Pageable pageable) {
        return PageResponse.from(
                userAccountRepository.findByCentreIdAndRole(
                        principal.centreId(), UserRole.TEACHER, pageable),
                TeacherAdminService::toDto);
    }

    /** Deactivation is soft: the account stays, its sessions die immediately. */
    @Transactional
    public void deactivateTeacher(LearniumPrincipal principal, UUID userId) {
        UserAccount teacher =
                userAccountRepository
                        .findByCentreIdAndUserId(principal.centreId(), userId)
                        .filter(user -> user.getRole() == UserRole.TEACHER)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USR_NOT_FOUND));
        teacher.setStatus(UserStatus.DISABLED);
        userAccountRepository.save(teacher);
        refreshTokenService.revokeAllForUser(teacher);
    }

    private static TeacherDto toDto(UserAccount user) {
        return new TeacherDto(
                user.getUserId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getStatus() == UserStatus.ACTIVE,
                user.getCreatedAt());
    }
}
