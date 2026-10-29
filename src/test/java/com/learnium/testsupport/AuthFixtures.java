package com.learnium.testsupport;

import com.learnium.model.common.UserStatus;
import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import com.learnium.repository.user.CentreRepository;
import com.learnium.repository.user.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Shared builders for auth-flow tests: centres and staff users with real BCrypt hashes. */
public final class AuthFixtures {

    private AuthFixtures() {}

    public static Centre createCentre(CentreRepository centreRepository, String name) {
        Centre centre = new Centre();
        centre.setName(name);
        return centreRepository.save(centre);
    }

    public static UserAccount createStaff(
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            Centre centre,
            UserRole role,
            String email,
            String displayName,
            String rawPassword,
            UserStatus status) {
        UserAccount user = new UserAccount();
        user.setCentre(centre);
        user.setRole(role);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(rawPassword == null ? null : passwordEncoder.encode(rawPassword));
        user.setStatus(status);
        return userAccountRepository.save(user);
    }
}
