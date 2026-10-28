package com.learnium.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import lombok.SneakyThrows;

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

    @SneakyThrows
    public static String sha256Hex(String rawToken) {
        // SHA-256 is guaranteed present on every JVM, so getInstance cannot
        // actually fail; @SneakyThrows keeps that fiction out of the API.
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    }
}
