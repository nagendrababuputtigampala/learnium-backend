package com.learnium.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EmailAddressesTest {

    @Test
    void trimsAndLowerCases() {
        assertThat(EmailAddresses.normalize("  Ann@Example.COM ")).isEqualTo("ann@example.com");
    }

    @Test
    void leavesAnAlreadyNormalisedEmailUnchanged() {
        assertThat(EmailAddresses.normalize("ann@example.com")).isEqualTo("ann@example.com");
    }

    @Test
    void nullStaysNull() {
        assertThat(EmailAddresses.normalize(null)).isNull();
    }
}
