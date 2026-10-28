package com.learnium.auth;

import com.learnium.common.EmailAddresses;
import com.learnium.model.auth.AccountTokenPurpose;
import com.learnium.model.common.UserStatus;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.user.UserAccountRepository;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The emailed-link account flows: forgot/reset password and teacher invite
 * acceptance. Token creation and the outbox row are written in the caller's
 * transaction, so a link is only ever emailed for a committed action.
 */
@Service
public class AccountFlowService {

    public static final Duration INVITE_TTL = Duration.ofHours(48);
    public static final Duration RESET_TTL = Duration.ofHours(1);

    private final UserAccountRepository userAccountRepository;
    private final AccountTokenService accountTokenService;
    private final EmailOutboxService emailOutboxService;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public AccountFlowService(
            UserAccountRepository userAccountRepository,
            AccountTokenService accountTokenService,
            EmailOutboxService emailOutboxService,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService) {
        this.userAccountRepository = userAccountRepository;
        this.accountTokenService = accountTokenService;
        this.emailOutboxService = emailOutboxService;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Enqueues an invite email for a freshly created account: a 48-hour
     * INVITE token plus its outbox row, in the caller's transaction.
     */
    @Transactional
    public void sendInvite(UserAccount user) {
        String raw = accountTokenService.create(user, AccountTokenPurpose.INVITE, INVITE_TTL);
        emailOutboxService.enqueue(
                user.getCentre().getCentreId(),
                user.getEmail(),
                "invite",
                mailModel(user, raw, "invite", INVITE_TTL));
    }

    /**
     * Resend an invite for an account that has not set a password yet: every
     * outstanding invite token is invalidated and a fresh 48-hour token +
     * email replace them, in the caller's transaction.
     */
    @Transactional
    public void resendInvite(UserAccount user) {
        accountTokenService.invalidateOutstanding(user, AccountTokenPurpose.INVITE);
        sendInvite(user);
    }

    /**
     * Forgot-password: deliberately silent about whether the email exists
     * (the endpoint always answers 202). Only active staff accounts get a
     * reset link — students have no password to reset.
     */
    @Transactional
    public void forgotPassword(String email) {
        for (UserAccount user : userAccountRepository.findByEmail(EmailAddresses.normalize(email))) {
            if (user.getStatus() == UserStatus.ACTIVE && user.getRole() != UserRole.STUDENT) {
                String raw = accountTokenService.create(user, AccountTokenPurpose.RESET, RESET_TTL);
                emailOutboxService.enqueue(
                        user.getCentre().getCentreId(),
                        user.getEmail(),
                        "reset",
                        mailModel(user, raw, "reset", RESET_TTL));
            }
        }
    }

    /** Reset: redeem the token, set the password, kill every live session. */
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        UserAccount user = accountTokenService.consume(rawToken, AccountTokenPurpose.RESET);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // Same transaction as the credential change: every token stamped
        // with an older version is refused at rotation, including any a
        // racing old-password login inserts after the revoke-all below.
        user.setSecurityVersion(user.getSecurityVersion() + 1);
        userAccountRepository.save(user);
        refreshTokenService.revokeAllForUser(user);
    }

    /** Invite acceptance: redeem the token and set the account's first password. */
    @Transactional
    public void acceptInvite(String rawToken, String newPassword) {
        UserAccount user = accountTokenService.consume(rawToken, AccountTokenPurpose.INVITE);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setSecurityVersion(user.getSecurityVersion() + 1);
        userAccountRepository.save(user);
    }

    private Map<String, Object> mailModel(
            UserAccount user, String rawToken, String purpose, Duration ttl) {
        Map<String, Object> model = new HashMap<>();
        model.put("displayName", user.getDisplayName());
        model.put("centreName", user.getCentre().getName());
        model.put("token", rawToken);
        model.put("purpose", purpose);
        model.put("expiresHours", Long.toString(ttl.toHours()));
        return model;
    }
}
