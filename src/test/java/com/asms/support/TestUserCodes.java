package com.asms.support;

import java.util.Locale;

/**
 * User IDs (the sign-in identifier) for test accounts, derived from their email so that a test can log in with
 * only the email it created the account with.
 */
public final class TestUserCodes {

    private TestUserCodes() {}

    /** Same email, same code; uppercase alphanumeric like a normalized code. */
    public static String codeFor(String email) {
        return "T" + Integer.toUnsignedString(email.hashCode(), 36).toUpperCase(Locale.ROOT);
    }
}
