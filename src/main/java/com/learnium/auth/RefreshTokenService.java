package com.learnium.auth;

import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.auth.RefreshToken;
import com.learnium.model.user.UserAccount;
import com.learnium.repository.auth.RefreshTokenRepository;
import com.learnium.security.TokenHashing;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token lifecycle: issue, rotate, revoke. Tokens are rotating — every
 * refresh consumes the presented token and issues a new one. Presenting an
 * already-revoked token means an old token was replayed (theft signal): every
 * token of that user is revoked and the request fails with 401.
 */
@Service
public class RefreshTokenService {

    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /** The user a successful rotation authenticated, plus the fresh raw token. */
    public record Rotation(UserAccount user, String rawRefreshToken) {}

    @Transactional
    public String issue(UserAccount user) {
        String raw = TokenHashing.newRawToken();
        Instant now = Instant.now();
        RefreshToken token = new RefreshToken();
        token.setCentreId(user.getCentre().getCentreId());
        token.setUser(user);
        token.setTokenHash(TokenHashing.sha256Hex(raw));
        token.setExpiresAt(now.plus(REFRESH_TOKEN_TTL));
        token.setCreatedAt(now);
        refreshTokenRepository.save(token);
        return raw;
    }

    /**
     * Consumes the presented token and issues its replacement.
     *
     * @throws BusinessException {@code LRN-AUTH-002} for unknown, expired or
     *     replayed tokens (replay also revokes the user's whole token family)
     */
    @Transactional
    public Rotation rotate(String rawToken) {
        RefreshToken presented =
                refreshTokenRepository
                        .findByTokenHash(TokenHashing.sha256Hex(rawToken))
                        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
        Instant now = Instant.now();
        UserAccount user = presented.getUser();
        if (presented.getRevokedAt() != null) {
            // Reuse of a rotated/revoked token: kill the whole family.
            refreshTokenRepository.revokeAllByCentreIdAndUserId(
                    presented.getCentreId(), user.getUserId(), now);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if (presented.getExpiresAt().isBefore(now)) {
            presented.setRevokedAt(now);
            refreshTokenRepository.save(presented);
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        presented.setRevokedAt(now);
        refreshTokenRepository.save(presented);
        return new Rotation(user, issue(user));
    }

    /** Logout: revoke just the presented token; unknown tokens are a no-op. */
    @Transactional
    public void revokeByRawToken(String rawToken) {
        refreshTokenRepository
                .findByTokenHash(TokenHashing.sha256Hex(rawToken))
                .ifPresent(
                        token -> {
                            if (token.getRevokedAt() == null) {
                                token.setRevokedAt(Instant.now());
                                refreshTokenRepository.save(token);
                            }
                        });
    }

    /** Revoke every live token of a user (password reset, deactivation). */
    @Transactional
    public void revokeAllForUser(UserAccount user) {
        refreshTokenRepository.revokeAllByCentreIdAndUserId(
                user.getCentre().getCentreId(), user.getUserId(), Instant.now());
    }
}
