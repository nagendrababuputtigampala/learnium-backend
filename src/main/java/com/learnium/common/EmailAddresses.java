package com.learnium.common;

import java.util.Locale;

/**
 * The one canonical form of an email address in this system: trimmed and
 * lower-cased. Staff emails are globally unique (Flyway V8 enforces it on
 * {@code lower(email)} in the database), so every write and every lookup
 * normalises through here — {@code Ann@Example.com} and
 * {@code ann@example.com} are the same account everywhere.
 */
public final class EmailAddresses {

    private EmailAddresses() {}

    /** Normalises an email for storage or lookup; {@code null} stays {@code null}. */
    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
