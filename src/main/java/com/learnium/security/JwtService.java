package com.learnium.security;

import com.learnium.model.user.UserAccount;
import com.learnium.model.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and validates Learnium access tokens (HS256). Staff tokens live 15
 * minutes; a student session token lives 12 hours and is additionally scoped
 * to one batch + roster membership. The signing secret comes from
 * {@code JWT_SECRET}; when it is blank (tests set their own), a deterministic
 * development fallback keeps the app startable — production must set the env
 * var (docker-compose ships a labelled dev-only value).
 */
@Service
public class JwtService {

    public static final Duration STAFF_TOKEN_TTL = Duration.ofMinutes(15);
    public static final Duration STUDENT_TOKEN_TTL = Duration.ofHours(12);

    private static final String CLAIM_CENTRE_ID = "centreId";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "typ";
    private static final String CLAIM_BATCH_ID = "batchId";
    private static final String CLAIM_MEMBERSHIP_ID = "membershipId";
    private static final String TYPE_ACCESS = "access";
    private static final String DEV_FALLBACK_SECRET =
            "learnium-dev-only-jwt-secret-not-for-production-use-0123456789";

    private final SecretKey signingKey;

    public JwtService(@Value("${learnium.jwt.secret:}") String secret) {
        String effective = (secret == null || secret.isBlank()) ? DEV_FALLBACK_SECRET : secret;
        this.signingKey = deriveKey(effective);
    }

    /**
     * The signing key is the SHA-256 of the configured secret: HS256 needs a
     * >= 256-bit key, and hashing makes any operator-supplied secret length
     * produce a full-strength key instead of failing (or silently weakening).
     */
    private static SecretKey deriveKey(String secret) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Keys.hmacShaKeyFor(digest.digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM; reaching this catch
            // means a broken runtime, not bad input.
            throw new IllegalStateException("SHA-256 MessageDigest is unavailable", e);
        }
    }

    /** Staff access token for an admin/teacher (15 min). */
    public String issueStaffToken(UserAccount user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUserId().toString())
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_CENTRE_ID, user.getCentre().getCentreId().toString())
                .claim(CLAIM_ROLE, user.getRole().name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(STAFF_TOKEN_TTL)))
                .signWith(signingKey)
                .compact();
    }

    /** Student session token (12 h), scoped to one batch roster membership. */
    public String issueStudentToken(
            UserAccount user, UUID centreId, UUID batchId, UUID membershipId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUserId().toString())
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_CENTRE_ID, centreId.toString())
                .claim(CLAIM_ROLE, UserRole.STUDENT.name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_BATCH_ID, batchId.toString())
                .claim(CLAIM_MEMBERSHIP_ID, membershipId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(STUDENT_TOKEN_TTL)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates signature, expiry and token type. Any failure yields empty —
     * the caller (filter) simply leaves the request unauthenticated and the
     * security chain produces the standard 401 envelope.
     */
    public Optional<LearniumPrincipal> validate(String token) {
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(signingKey)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            UUID userId = UUID.fromString(claims.getSubject());
            UUID centreId = UUID.fromString(claims.get(CLAIM_CENTRE_ID, String.class));
            UserRole role = UserRole.valueOf(claims.get(CLAIM_ROLE, String.class));
            String batchId = claims.get(CLAIM_BATCH_ID, String.class);
            String membershipId = claims.get(CLAIM_MEMBERSHIP_ID, String.class);
            if (role == UserRole.STUDENT) {
                if (batchId == null || membershipId == null) {
                    return Optional.empty();
                }
                return Optional.of(
                        LearniumPrincipal.student(
                                userId, centreId, UUID.fromString(batchId), UUID.fromString(membershipId)));
            }
            return Optional.of(LearniumPrincipal.staff(userId, centreId, role));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
