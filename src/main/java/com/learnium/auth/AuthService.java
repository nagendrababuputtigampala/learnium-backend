package com.learnium.auth;

import com.learnium.api.dto.AuthDtos.AuthResponse;
import com.learnium.api.dto.AuthDtos.MeResponse;
import com.learnium.api.dto.AuthDtos.UserSummary;
import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.common.UserStatus;
import com.learnium.model.user.UserAccount;
import com.learnium.repository.user.UserAccountRepository;
import com.learnium.security.JwtService;
import com.learnium.security.LearniumPrincipal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff login / refresh / logout / me. Staff emails are globally unique
 * (enforced where accounts are created), so the login door looks the email up
 * across centres and lets the password pick the account.
 */
@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            RefreshTokenService refreshTokenService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Unknown email and wrong password produce the identical 401 — the door
     * never reveals which part failed. A deactivated account whose password
     * DOES match gets the distinct 403 (the caller proved the credential).
     */
    @Transactional
    public AuthResponse login(String email, String password) {
        List<UserAccount> passwordMatches = new ArrayList<>();
        for (UserAccount candidate : userAccountRepository.findByEmail(email)) {
            if (candidate.getPasswordHash() != null
                    && passwordEncoder.matches(password, candidate.getPasswordHash())) {
                passwordMatches.add(candidate);
            }
        }
        if (passwordMatches.size() != 1) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        UserAccount user = passwordMatches.get(0);
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_INACTIVE);
        }
        user.setLastLoginAt(Instant.now());
        userAccountRepository.save(user);
        return buildResponse(user, refreshTokenService.issue(user));
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);
        UserAccount user = rotation.user();
        if (user.getStatus() != UserStatus.ACTIVE) {
            // The rotation already consumed the old token; kill the new one too.
            refreshTokenService.revokeAllForUser(user);
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_INACTIVE);
        }
        return buildResponse(user, rotation.rawRefreshToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeByRawToken(rawRefreshToken);
    }

    @Transactional(readOnly = true)
    public MeResponse me(LearniumPrincipal principal) {
        UserAccount user =
                userAccountRepository
                        .findByCentreIdAndUserId(principal.centreId(), principal.userId())
                        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHENTICATED));
        return new MeResponse(
                user.getUserId(),
                user.getDisplayName(),
                user.getEmail(),
                user.getRole().name(),
                user.getCentre().getCentreId(),
                user.getCentre().getName());
    }

    private AuthResponse buildResponse(UserAccount user, String rawRefreshToken) {
        return new AuthResponse(
                jwtService.issueStaffToken(user),
                rawRefreshToken,
                "Bearer",
                JwtService.STAFF_TOKEN_TTL.toSeconds(),
                new UserSummary(
                        user.getUserId(),
                        user.getDisplayName(),
                        user.getEmail(),
                        user.getRole().name(),
                        user.getCentre().getCentreId(),
                        user.getCentre().getName()));
    }
}
