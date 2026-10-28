package com.learnium.mail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.learnium.security.JwtService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts email-outbox payloads at rest. The payload carries a raw,
 * single-use set-password token — a database reader must not be able to
 * redeem it — so {@code EmailOutboxService} stores only an AES-GCM
 * ciphertext and the dispatcher decrypts in memory at send time.
 *
 * <p>Key material: {@code OUTBOX_ENCRYPTION_KEY} when configured; otherwise
 * derived from the JWT secret material, so existing deployments need no new
 * configuration (rotate the outbox key independently once real traffic
 * matters; undelivered rows encrypted under an old key simply fail their
 * remaining retries). The stored form is a JSON object
 * {@code {"enc": base64(iv ‖ ciphertext)}}; a payload without that field is
 * a legacy plaintext row and passes through unchanged. Keys and plaintext
 * are never logged.
 */
@Component
public class PayloadCipher {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String FIELD = "enc";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public PayloadCipher(
            @Value("${learnium.outbox.encryption-key:}") String encryptionKey,
            @Value("${learnium.jwt.secret:}") String jwtSecret) {
        this.key = new SecretKeySpec(sha256(keyMaterial(encryptionKey, jwtSecret)), "AES");
    }

    private static String keyMaterial(String encryptionKey, String jwtSecret) {
        if (encryptionKey != null && !encryptionKey.isBlank()) {
            return encryptionKey;
        }
        if (jwtSecret != null && !jwtSecret.isBlank()) {
            return jwtSecret;
        }
        return JwtService.DEV_FALLBACK_SECRET;
    }

    private static byte[] sha256(String material) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM; reaching this catch
            // means a broken runtime, not bad input.
            throw new IllegalStateException("SHA-256 MessageDigest is unavailable", e);
        }
    }

    /** Encrypts a payload document for storage in the outbox. */
    public JsonNode encrypt(JsonNode plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(JSON.writeValueAsBytes(plain));
            byte[] packed = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(ciphertext, 0, packed, iv.length, ciphertext.length);
            ObjectNode stored = JSON.createObjectNode();
            stored.put(FIELD, Base64.getEncoder().encodeToString(packed));
            return stored;
        } catch (GeneralSecurityException | JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload encryption failed", e);
        }
    }

    /**
     * Decrypts a stored payload. Legacy rows written before encryption
     * existed (no {@code enc} field) are returned as-is.
     *
     * @throws IllegalStateException when a ciphertext is corrupt, tampered
     *     with, or encrypted under a different key
     */
    public JsonNode decrypt(JsonNode stored) {
        JsonNode enc = stored.get(FIELD);
        if (enc == null || !enc.isTextual()) {
            return stored;
        }
        try {
            byte[] packed = Base64.getDecoder().decode(enc.asText());
            byte[] iv = Arrays.copyOfRange(packed, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(packed, IV_BYTES, packed.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return JSON.readTree(cipher.doFinal(ciphertext));
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Outbox payload decryption failed", e);
        }
    }
}
