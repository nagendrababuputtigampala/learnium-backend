package com.learnium.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

/** Outbox payload encryption: key derivation order, round trip, legacy rows, tampering. */
class PayloadCipherTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private JsonNode samplePayload() {
        ObjectNode node = JSON.createObjectNode();
        node.put("displayName", "Tess Teacher");
        node.put("token", "raw-token-123");
        node.put("purpose", "invite");
        return node;
    }

    @Test
    void roundTripsWithAnExplicitEncryptionKey() {
        PayloadCipher cipher = new PayloadCipher("outbox-key-material", "jwt-secret-material");

        JsonNode stored = cipher.encrypt(samplePayload());

        assertThat(stored.has("enc")).isTrue();
        assertThat(stored.toString()).doesNotContain("raw-token-123");
        assertThat(cipher.decrypt(stored)).isEqualTo(samplePayload());
    }

    @Test
    void derivesTheKeyFromTheJwtSecretWhenNoOutboxKeyIsConfigured() {
        PayloadCipher cipher = new PayloadCipher("", "jwt-secret-material");
        PayloadCipher sameMaterial = new PayloadCipher(null, "jwt-secret-material");

        JsonNode stored = cipher.encrypt(samplePayload());

        assertThat(sameMaterial.decrypt(stored)).isEqualTo(samplePayload());
    }

    @Test
    void fallsBackToTheDevelopmentMaterialWhenNothingIsConfigured() {
        PayloadCipher cipher = new PayloadCipher(null, null);
        PayloadCipher blank = new PayloadCipher(" ", "");

        JsonNode stored = cipher.encrypt(samplePayload());

        assertThat(blank.decrypt(stored)).isEqualTo(samplePayload());
    }

    @Test
    void aPayloadEncryptedUnderAnotherKeyDoesNotDecrypt() {
        PayloadCipher cipher = new PayloadCipher("key-one", null);
        PayloadCipher other = new PayloadCipher("key-two", null);
        JsonNode stored = cipher.encrypt(samplePayload());

        assertThatThrownBy(() -> other.decrypt(stored))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("decryption failed");
    }

    @Test
    void corruptCiphertextIsRejected() {
        PayloadCipher cipher = new PayloadCipher("key", null);
        ObjectNode stored = JSON.createObjectNode();
        stored.put("enc", "!!! not base64 !!!");

        assertThatThrownBy(() -> cipher.decrypt(stored))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void legacyPlaintextPayloadsPassThroughUnchanged() {
        PayloadCipher cipher = new PayloadCipher("key", null);

        assertThat(cipher.decrypt(samplePayload())).isEqualTo(samplePayload());

        ObjectNode nonTextual = JSON.createObjectNode();
        nonTextual.put("enc", 42);
        assertThat(cipher.decrypt(nonTextual)).isEqualTo(nonTextual);
    }

    @Test
    void eachEncryptionUsesAFreshIv() {
        PayloadCipher cipher = new PayloadCipher("key", null);

        assertThat(cipher.encrypt(samplePayload()).toString())
                .isNotEqualTo(cipher.encrypt(samplePayload()).toString());
    }
}
