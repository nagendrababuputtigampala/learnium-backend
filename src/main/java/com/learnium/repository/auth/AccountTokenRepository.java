package com.learnium.repository.auth;

import com.learnium.model.auth.AccountToken;
import com.learnium.model.auth.AccountTokenPurpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountTokenRepository extends JpaRepository<AccountToken, UUID> {

    /** The token hash is the credential: lookup is by hash + purpose, never by raw token. */
    @Query(
            "select t from AccountToken t"
                    + " where t.tokenHash = :tokenHash and t.purpose = :purpose")
    Optional<AccountToken> findByTokenHashAndPurpose(
            @Param("tokenHash") String tokenHash, @Param("purpose") AccountTokenPurpose purpose);

    /**
     * The atomic consume: stamps {@code usedAt} ONLY if the token is still
     * unused and unexpired. Returns 1 for the single winning consumer, 0 for
     * everyone else — two concurrent resets with one token can therefore
     * never both change the password.
     */
    @Modifying(flushAutomatically = true)
    @Query(
            "update AccountToken t set t.usedAt = :now"
                    + " where t.accountTokenId = :id and t.usedAt is null and t.expiresAt > :now")
    int consumeIfRedeemable(@Param("id") UUID id, @Param("now") Instant now);

    /**
     * Invalidates every outstanding (unused) token of one purpose for a user —
     * used by resend-invite so only the freshest link can ever be redeemed.
     */
    @Modifying(flushAutomatically = true)
    @Query(
            "update AccountToken t set t.usedAt = :now"
                    + " where t.user.userId = :userId and t.purpose = :purpose"
                    + " and t.usedAt is null")
    int invalidateOutstanding(
            @Param("userId") UUID userId,
            @Param("purpose") AccountTokenPurpose purpose,
            @Param("now") Instant now);
}
