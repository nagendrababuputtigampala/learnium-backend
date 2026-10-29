package com.learnium.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Raw-token generation and hashing shared by refresh tokens and account
 * tokens. Raw tokens are 32 random bytes, Base64URL-encoded; only their
 * SHA-256 hex digest is ever stored, so a database leak yields no usable
 * credentials.
 */
public final class TokenHashing {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private TokenHashing() {}

    public static String newRawToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** A fresh SHA-256 digest for incremental hashing of streamed content. */
    public static MessageDigest newSha256Digest() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest;
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM; reaching this catch
            // means a broken runtime, not bad input.
            throw new IllegalStateException("SHA-256 MessageDigest is unavailable", e);
        }
    }

    public static String sha256Hex(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM; reaching this catch
            // means a broken runtime, not bad input.
            throw new IllegalStateException("SHA-256 MessageDigest is unavailable", e);
        }
    }
}
