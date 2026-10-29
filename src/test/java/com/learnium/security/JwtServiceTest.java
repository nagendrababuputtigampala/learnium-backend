package com.learnium.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.learnium.model.user.Centre;
import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-0123456789";

    private final JwtService jwtService = service(SECRET, false);

    /** Builds a service with an explicit gate configuration (flag + active profiles). */
    private static JwtService service(String secret, boolean devSecretAllowed, String... profiles) {
        org.springframework.mock.env.MockEnvironment environment =
                new org.springframework.mock.env.MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new JwtService(secret, devSecretAllowed, environment);
    }

    /** Mirrors JwtService's key derivation (SHA-256 of the secret) for forging test tokens. */
    private static SecretKey keyFor(String secret) throws Exception {
        byte[] hashed =
                java.security.MessageDigest.getInstance("SHA-256")
                        .digest(secret.getBytes(StandardCharsets.UTF_8));
        return Keys.hmacShaKeyFor(hashed);
    }

    private UserAccount staffUser(UserRole role) {
        Centre centre = new Centre();
        centre.setCentreId(UUID.randomUUID());
        centre.setName("JWT Centre");
        UserAccount user = new UserAccount();
        user.setUserId(UUID.randomUUID());
        user.setCentre(centre);
        user.setRole(role);
        return user;
    }

    @Test
    void staffTokenRoundTripsToPrincipal() {
        UserAccount user = staffUser(UserRole.TEACHER);

        String token = jwtService.issueStaffToken(user);

        assertThat(jwtService.validate(token))
                .contains(
                        LearniumPrincipal.staff(
                                user.getUserId(), user.getCentre().getCentreId(), UserRole.TEACHER));
    }

    @Test
    void studentTokenCarriesBatchAndMembershipScope() {
        UserAccount user = staffUser(UserRole.STUDENT);
        UUID centreId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        UUID membershipId = UUID.randomUUID();

        String token = jwtService.issueStudentToken(user, centreId, batchId, membershipId);

        assertThat(jwtService.validate(token))
                .contains(LearniumPrincipal.student(user.getUserId(), centreId, batchId, membershipId));
    }

    @Test
    void blankSecretFallsBackToDevelopmentSecretOnDemoProfile() {
        JwtService fallback = service("", false, "demo");
        UserAccount user = staffUser(UserRole.CENTRE_ADMIN);

        String token = fallback.issueStaffToken(user);

        assertThat(fallback.validate(token)).isPresent();
        // A token from the fallback-secret service must not validate elsewhere.
        assertThat(jwtService.validate(token)).isEmpty();
    }

    @Test
    void blankSecretFallsBackToDevelopmentSecretOnDevProfile() {
        JwtService fallback = service("", false, "dev");

        String token = fallback.issueStaffToken(staffUser(UserRole.TEACHER));

        assertThat(fallback.validate(token)).isPresent();
    }

    @Test
    void blankSecretFallsBackWhenDevSecretFlagIsEnabled() {
        JwtService fallback = service("", true, "prod");

        String token = fallback.issueStaffToken(staffUser(UserRole.TEACHER));

        assertThat(fallback.validate(token)).isPresent();
    }

    @Test
    void blankSecretWithoutAnyGateFailsStartup() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service("", false, "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET")
                .hasMessageContaining("JWT_DEV_SECRET_ALLOWED");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service("", false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void developmentLiteralCountsAsNoRealSecretAndFailsWithoutGate() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> service(JwtService.DEV_FALLBACK_SECRET, false, "prod"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void developmentLiteralWithGateOpenUsesTheFallback() {
        JwtService fallback = service(JwtService.DEV_FALLBACK_SECRET, false, "demo");

        String token = fallback.issueStaffToken(staffUser(UserRole.TEACHER));

        assertThat(fallback.validate(token)).isPresent();
    }

    @Test
    void realSecretIsUsedAsIsEvenWithGateClosed() {
        JwtService prod = service(SECRET, false, "prod");
        UserAccount user = staffUser(UserRole.TEACHER);

        String token = prod.issueStaffToken(user);

        assertThat(prod.validate(token)).isPresent();
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.issueStaffToken(staffUser(UserRole.TEACHER));

        assertThat(jwtService.validate(token + "x")).isEmpty();
        assertThat(jwtService.validate("not-a-jwt")).isEmpty();
        assertThat(jwtService.validate("")).isEmpty();
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        UserAccount user = staffUser(UserRole.TEACHER);
        SecretKey key = keyFor(SECRET);
        Instant past = Instant.now().minusSeconds(3600);
        String expired =
                Jwts.builder()
                        .subject(user.getUserId().toString())
                        .claim("centreId", user.getCentre().getCentreId().toString())
                        .claim("role", "TEACHER")
                        .claim("typ", "access")
                        .issuedAt(Date.from(past))
                        .expiration(Date.from(past.plusSeconds(60)))
                        .signWith(key)
                        .compact();

        assertThat(jwtService.validate(expired)).isEmpty();
    }

    @Test
    void nonAccessTokenTypeIsRejected() throws Exception {
        UserAccount user = staffUser(UserRole.TEACHER);
        SecretKey key = keyFor(SECRET);
        String refreshTyped =
                Jwts.builder()
                        .subject(user.getUserId().toString())
                        .claim("centreId", user.getCentre().getCentreId().toString())
                        .claim("role", "TEACHER")
                        .claim("typ", "refresh")
                        .issuedAt(new Date())
                        .expiration(Date.from(Instant.now().plusSeconds(600)))
                        .signWith(key)
                        .compact();

        assertThat(jwtService.validate(refreshTyped)).isEmpty();
    }

    @Test
    void nullSecretFallsBackToDevelopmentSecretWhenFlagIsEnabled() {
        JwtService fallback = service(null, true);
        UserAccount user = staffUser(UserRole.TEACHER);

        String token = fallback.issueStaffToken(user);

        assertThat(fallback.validate(token)).isPresent();
    }

    @Test
    void studentTokenWithoutMembershipClaimIsRejected() throws Exception {
        UserAccount user = staffUser(UserRole.STUDENT);
        SecretKey key = keyFor(SECRET);
        String batchOnly =
                Jwts.builder()
                        .subject(user.getUserId().toString())
                        .claim("centreId", user.getCentre().getCentreId().toString())
                        .claim("role", "STUDENT")
                        .claim("typ", "access")
                        .claim("batchId", UUID.randomUUID().toString())
                        .issuedAt(new Date())
                        .expiration(Date.from(Instant.now().plusSeconds(600)))
                        .signWith(key)
                        .compact();

        assertThat(jwtService.validate(batchOnly)).isEmpty();
    }

    @Test
    void constructorFailsWhenSha256IsUnavailable() {
        // SHA-256 comes from the SUN provider; removing it simulates a broken
        // runtime so the defensive catch in deriveKey is exercised for real.
        java.security.Provider sun = java.security.Security.getProvider("SUN");
        java.security.Security.removeProvider("SUN");
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> service(SECRET, false))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SHA-256");
        } finally {
            java.security.Security.addProvider(sun);
        }
    }

    @Test
    void studentTokenWithoutBatchScopeIsRejected() throws Exception {
        UserAccount user = staffUser(UserRole.STUDENT);
        SecretKey key = keyFor(SECRET);
        String unscoped =
                Jwts.builder()
                        .subject(user.getUserId().toString())
                        .claim("centreId", user.getCentre().getCentreId().toString())
                        .claim("role", "STUDENT")
                        .claim("typ", "access")
                        .issuedAt(new Date())
                        .expiration(Date.from(Instant.now().plusSeconds(600)))
                        .signWith(key)
                        .compact();

        assertThat(jwtService.validate(unscoped)).isEmpty();
    }
}
