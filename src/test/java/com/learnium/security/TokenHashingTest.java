package com.learnium.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.Provider;
import java.security.Security;
import org.junit.jupiter.api.Test;

/** Raw-token generation + SHA-256 hashing (refresh/account token storage). */
class TokenHashingTest {

    @Test
    void sha256HexMatchesTheKnownVector() {
        // FIPS 180-4 example: SHA-256("abc").
        assertThat(TokenHashing.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void rawTokensAre43CharBase64UrlAndUnique() {
        String first = TokenHashing.newRawToken();
        String second = TokenHashing.newRawToken();

        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void sha256HexFailsWhenSha256IsUnavailable() {
        // Force class initialization first: TokenHashing's static SecureRandom
        // must not be created inside the provider-removal window below.
        TokenHashing.newRawToken();
        // SHA-256 comes from the SUN provider; removing it simulates a broken
        // runtime so the defensive catch is exercised for real.
        Provider sun = Security.getProvider("SUN");
        Security.removeProvider("SUN");
        try {
            assertThatThrownBy(() -> TokenHashing.sha256Hex("anything"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SHA-256");
        } finally {
            Security.addProvider(sun);
        }
    }

    @Test
    void newDigestHashesIncrementallyAndFailsWhenSha256IsUnavailable() {
        // The streaming factory produces a working digest…
        java.security.MessageDigest digest = TokenHashing.newSha256Digest();
        digest.update("ab".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        digest.update("c".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(java.util.HexFormat.of().formatHex(digest.digest()))
                .isEqualTo(TokenHashing.sha256Hex("abc"));

        // …and its defensive catch behaves exactly like sha256Hex's.
        Provider sun = Security.getProvider("SUN");
        Security.removeProvider("SUN");
        try {
            assertThatThrownBy(TokenHashing::newSha256Digest)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("SHA-256");
        } finally {
            Security.addProvider(sun);
        }
    }
}
