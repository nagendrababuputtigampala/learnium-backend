package com.learnium.auth;

import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.auth.RefreshToken;
import com.learnium.model.user.UserAccount;
import com.learnium.repository.auth.RefreshTokenRepository;
import com.learnium.security.TokenHashing;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Refresh-token lifecycle: issue, rotate, revoke. Tokens are rotating — every
 * refresh consumes the presented token and issues a new one. Presenting an
 * already-revoked token means an old token was replayed (theft signal): every
 * token of that user is revoked and the request fails with 401.
 *
 * <p>Transaction boundaries are explicit because the security state must
 * survive the rejection that follows it: consuming a token is one atomic
 * conditional update (a concurrent duplicate loses the race and is treated
 * exactly like a replay), and family revocation runs in an independent
 * REQUIRES_NEW transaction so the 401 thrown afterwards can never roll it
 * back. (The previous implementation revoked inside the request transaction
 * and then threw — the rollback silently resurrected the whole family.)
 */
@Service
public class RefreshTokenService {

    public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

    private final RefreshTokenRepository refreshTokenRepository;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate requiresNewTemplate;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            PlatformTransactionManager transactionManager) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.requiresNewTemplate = requiresNew;
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
    public Rotation rotate(String rawToken) {
        RefreshToken presented =
                refreshTokenRepository
                        .findByTokenHashWithUser(TokenHashing.sha256Hex(rawToken))
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS));
        if (presented.getRevokedAt() != null) {
            // Reuse of a rotated/revoked token: kill the whole family.
            revokeFamily(presented.getCentreId(), presented.getUser().getUserId());
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if (presented.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        Rotation rotation =
                transactionTemplate.execute(
                        status -> {
                            int consumed =
                                    refreshTokenRepository.consumeIfActive(
                                            presented.getRefreshTokenId(), Instant.now());
                            if (consumed == 0) {
                                return null;
                            }
                            return new Rotation(presented.getUser(), issue(presented.getUser()));
                        });
        if (rotation == null) {
            // A concurrent consumer won the race for this token. That is
            // indistinguishable from a replay, so it is handled like one.
            revokeFamily(presented.getCentreId(), presented.getUser().getUserId());
            throw new BusinessException(ErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        return rotation;
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

    /**
     * Revoke every live token of a user (password reset, deactivation). The
     * revocation always commits in its own transaction: callers may go on to
     * throw (e.g. refresh against a deactivated account), and the revocation
     * must not be rolled back with their transaction.
     */
    public void revokeAllForUser(UserAccount user) {
        revokeFamily(user.getCentre().getCentreId(), user.getUserId());
    }

    private void revokeFamily(UUID centreId, UUID userId) {
        requiresNewTemplate.executeWithoutResult(
                status ->
                        refreshTokenRepository.revokeAllByCentreIdAndUserId(
                                centreId, userId, Instant.now()));
    }
}
