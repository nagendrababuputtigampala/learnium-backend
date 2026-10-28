package com.learnium.auth;

import com.learnium.common.error.BusinessException;
import com.learnium.common.error.ErrorCode;
import com.learnium.model.auth.AccountToken;
import com.learnium.model.auth.AccountTokenPurpose;
import com.learnium.model.user.UserAccount;
import com.learnium.repository.auth.AccountTokenRepository;
import com.learnium.security.TokenHashing;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-use account tokens (invite / password reset). The raw token is
 * returned exactly once, at creation, for inclusion in the emailed link; the
 * database only ever holds its SHA-256 hash.
 */
@Service
public class AccountTokenService {

    private final AccountTokenRepository accountTokenRepository;

    public AccountTokenService(AccountTokenRepository accountTokenRepository) {
        this.accountTokenRepository = accountTokenRepository;
    }

    @Transactional
    public String create(UserAccount user, AccountTokenPurpose purpose, Duration ttl) {
        String raw = TokenHashing.newRawToken();
        AccountToken token = new AccountToken();
        token.setCentreId(user.getCentre().getCentreId());
        token.setUser(user);
        token.setPurpose(purpose);
        token.setTokenHash(TokenHashing.sha256Hex(raw));
        token.setExpiresAt(Instant.now().plus(ttl));
        accountTokenRepository.save(token);
        return raw;
    }

    /**
     * Redeems a token: it must exist for the given purpose, be unused and be
     * unexpired. Consumption is a single conditional update inside the
     * caller's transaction (the same transaction as the password change), so
     * exactly one of any number of concurrent redemptions can win — the
     * winner's {@code usedAt} stamp is what every other attempt observes.
     *
     * @throws BusinessException {@code LRN-AUTH-004} for every rejection — the
     *     caller cannot tell unknown, used and expired tokens apart.
     */
    @Transactional
    public UserAccount consume(String rawToken, AccountTokenPurpose purpose) {
        AccountToken token =
                accountTokenRepository
                        .findByTokenHashAndPurpose(TokenHashing.sha256Hex(rawToken), purpose)
                        .orElseThrow(() -> new BusinessException(ErrorCode.AUTH_ACCOUNT_TOKEN_INVALID));
        Instant now = Instant.now();
        int consumed = accountTokenRepository.consumeIfRedeemable(token.getAccountTokenId(), now);
        if (consumed == 0) {
            throw new BusinessException(ErrorCode.AUTH_ACCOUNT_TOKEN_INVALID);
        }
        token.setUsedAt(now);
        return token.getUser();
    }

    /**
     * Invalidates every outstanding (unused) token of one purpose for a user,
     * in the caller's transaction. Resend-invite uses it so a superseded link
     * dies in the same commit that issues its replacement.
     */
    @Transactional
    public void invalidateOutstanding(UserAccount user, AccountTokenPurpose purpose) {
        accountTokenRepository.invalidateOutstanding(user.getUserId(), purpose, Instant.now());
    }
}
