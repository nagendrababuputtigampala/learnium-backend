package com.learnium.model.auth;

/** What an {@link AccountToken} authorises its bearer to do, exactly once. */
public enum AccountTokenPurpose {
    /** Set the first password on an invited account. */
    INVITE,
    /** Reset a forgotten password. */
    RESET
}
