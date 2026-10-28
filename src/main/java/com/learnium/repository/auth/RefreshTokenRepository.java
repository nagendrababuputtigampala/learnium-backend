package com.learnium.repository.auth;

import com.learnium.model.auth.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Rotation read: the token with its user AND the user's centre
     * fetch-joined, so the caller can work with the detached result outside
     * a persistence context (without the second fetch join the centre
     * arrives as an uninitialized proxy).
     */
    @Query(
            "select t from RefreshToken t join fetch t.user u join fetch u.centre"
                    + " where t.tokenHash = :tokenHash")
    Optional<RefreshToken> findByTokenHashWithUser(@Param("tokenHash") String tokenHash);

    /**
     * The atomic rotation consume: marks the token revoked ONLY if it is
     * still live. Returns 1 for the single winning consumer, 0 when another
     * transaction already consumed it — the database arbitrates the race, so
     * two concurrent rotations can never both succeed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update RefreshToken t set t.revokedAt = :now"
                    + " where t.refreshTokenId = :id and t.revokedAt is null")
    int consumeIfActive(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * Revoke every still-live token of one user inside one centre (logout-all
     * paths). {@code clearAutomatically} matters: a bulk update bypasses the
     * persistence context, and callers must never act on a stale, still-live
     * in-memory token afterwards (the reuse-detection check depends on it).
     * {@code flushAutomatically} matters just as much: without it, a caller's
     * pending change on the user (e.g. a new password hash set moments
     * earlier) would be discarded by the clear instead of flushed first.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            "update RefreshToken t set t.revokedAt = :now"
                    + " where t.centreId = :centreId and t.user.userId = :userId"
                    + " and t.revokedAt is null")
    int revokeAllByCentreIdAndUserId(
            @Param("centreId") UUID centreId,
            @Param("userId") UUID userId,
            @Param("now") Instant now);
}
